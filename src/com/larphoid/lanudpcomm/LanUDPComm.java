package com.larphoid.lanudpcomm;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.R.drawable;
import android.R.string;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.format.Formatter;
import android.widget.BaseAdapter;
import android.widget.SimpleAdapter;

/**
 * © 2013 Larphoid Apps.
 * 
 * @since 05-09-2013
 * @author Larphoid Apps<br>
 * <br>
 *         <h1>- NOTICE -</h1> The first byte value in the byte[] parameter of {@link DatagramPacket#getData()} or {@link #onClientsEvent(byte[], DatagramPacket)} when sending {@link #sendClientPacket()} / {@link #sendClientPacket(String)} is reserved for internal use by LanUDPComm.<br>
 *         These byte values are: 0, 1, 2, 3, 4, 5, 6, 7 and 8, which mean NO, YES, STRING_SPLIT_SEPARATOR, HI, BYE, CLIENT_INVITE, BUSY, KEEP_ALIVE, and CLIENT_EVENT respectively.<br>
 *         If you need further communication commands, store them in the next byte(s) when using {@link #sendClientPacket()} or in the String when using {@link #sendClientPacket(String)}.<br>
 */
public class LanUDPComm implements ClientsEventHandler, ClientInviteHandler, ClientEventHandler {
	// private static final String TAG = "com.larphoid.lancommander";
	static final String NO = "\u0000";
	static final String YES = "\u0001";
	private static final String STRING_SPLIT_SEPARATOR = "\u0002";
	private static final String HI = "\u0003";
	private static final String BYE = "\u0004";
	private static final byte BYEBYTE = BYE.getBytes()[0];
	private static final String CLIENTINVITE = "\u0005";
	private static final byte[] BUSY = new byte[] {
		6
	};
	private static final byte[] KEEPALIVE = new byte[] {
		7
	};
	private static final byte CLIENTEVENT = 8;

	public static final int MAX_PACKETSIZE = 0x400;

	public static final int TIMESTRING_SHORT = SimpleDateFormat.SHORT;
	public static final int TIMESTRING_MEDIUM = SimpleDateFormat.MEDIUM;
	public static final int TIMESTRING_LONG = SimpleDateFormat.LONG;
	public static final int TIMESTRING_FULL = SimpleDateFormat.FULL;

	/**
	 * Use this when instantiating your clients SimpleAdapter before passing that adapter to {@link #setClientsAdapter(BaseAdapter)}.
	 */
	public static final String[] FROM_CLIENTS = new String[] {
		"name"
	};
	/**
	 * Used internally when getting the messages list adapter with {@link #getMessagesAdapter(Context, int, int[])}.
	 */
	private static final String[] FROM_MESSAGES = new String[] {
		"time", // the String timestamp of the message
		"from", // the destination client name
		"text" // the text content of the message
	};

	static String myIp;
	static int discovery_port = 20193;
	private final Activity activity;
	private final DialogInterface.OnDismissListener dialogDismissListener;
	private int comm_port = 12345;
	private int maxClientEventPacketSize;
	private ByteBuffer clientEventBuffer;
	private ClientInviteHandler clientInviteHandler;
	private ClientEventHandler clientEventHandler;
	private String displayName;
	/**
	 * Whether or not a connection needs to be 'alive'. This is set to the corresponding {@code pNeedAliveConnection} parameter when instantiating {@link LanUDPComm} from your application.<br>
	 * If {@code true}, {@link LanUDPComm} will send invite requests, show a waiting dialog until the client responds, and will inform back about accept/decline.<br>
	 * If {@code false}, there is no checking if the client responds or not, and data can always be send to the last {@link #inviteClientForConnection} client.
	 */
	private final boolean needAliveConnection;
	private byte[][] clienteventBuffers = new byte[4][];
	private int clienteventIndex = 0;
	private ClientEventListener clientEventListener;
	private WifiManager wifiManager;
	private DhcpInfo dhcp;
	// private MulticastLock mlock; // nodig voor multicast, niet nodig voor Discovery. Heb ik nu niet nodig voor 1 tegen 1.
	private Discovery discoveryListener;
	private Clients clients;
	private List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
	private DatagramSocket universalSocket;
	private DatagramPacket isAlivePacket;
	/**
	 * Use this whenever you want to show a waiting dialog. Simply call {@code yourLanUDPCommInstance.waitForInviteResponse.show()} and {@code yourLanUDPCommInstance.waitForInviteResponse.dismiss()} to hide it again.
	 */
	public ProgressDialog waitForInviteResponse;
	private AlertDialog inviteDialog;
	/**
	 * The client that is inviting you. This will get set to the InetAddress when receiving a {@link #CLIENTINVITE} event.
	 */
	private InetAddress invitingClient;
	/**
	 * The client you are inviting. This will get set to the corresponding InetAddress when calling {@link #inviteClientForConnection(int, String[])}.
	 */
	private InetAddress invitedClient;
	private boolean connected = false;
	private BaseAdapter clientsAdapter;
	private BaseAdapter messagesAdapter;

	/**
	 * @param pActivity
	 *            Activity that {@link LanUDPComm} operates on, for instance to show dialogs on the application's UI thread.
	 * @param pDialogDismissListener
	 *            Callback interface that will be called when any of the dialogs are dismissed. Can be {@code null} if not needed.
	 * @param pDiscoveryPort
	 *            The discovery port number. If <= 0, it will be set to LanCommander's internal default.
	 * @param pClientCommunicationPort
	 *            The communications port for direct client to client communication. If <= 0, defaults to LanCommander's internal default.
	 * @param pMaxClientEventPacketSize
	 *            The maximun packet size for sending {@link DatagramPacket}s to a connected client. If {@code <= 0} defaults to 1024.
	 * @param pClientInterface
	 *            Required. A {@link ClientInviteHandler} interface to receive notifications about events like invite, inivite response and the start of a connection. If not needed, simply do nothing in the methods.
	 * @param pClientEventHandler
	 *            Required. A {@link ClientEventHandler} interface to receive client to client communication. If not needed, simply do nothing in the methods.
	 * @param pMyDisplayName
	 *            The name of the user. Can be {@code null} if not needed. In that case, this client's ip is used instead.
	 * @param pNeedAliveConnection
	 *            Whether or not a connection needs to be 'alive'.<br>
	 *            If {@code true}, {@link LanUDPComm} will send invite requests, show a waiting dialog until the client responds, and will inform back about accept/decline.<br>
	 *            If {@code false}, there is no checking if the client responds or not, and data can always be send to the last {@link #inviteClientForConnection} client.
	 */
	public LanUDPComm(Activity pActivity, DialogInterface.OnDismissListener pDialogDismissListener, final int pDiscoveryPort, final int pClientCommunicationPort, final int pMaxClientEventPacketSize, final ClientInviteHandler pClientInterface, final ClientEventHandler pClientEventHandler, final String pMyDisplayName, final boolean pNeedAliveConnection) {
		activity = pActivity;
		dialogDismissListener = pDialogDismissListener;
		discovery_port = pDiscoveryPort > 0 ? pDiscoveryPort : discovery_port;
		comm_port = pClientCommunicationPort > 0 ? pClientCommunicationPort : comm_port;
		maxClientEventPacketSize = pMaxClientEventPacketSize > 0 ? pMaxClientEventPacketSize + 1 : MAX_PACKETSIZE + 1;
		for (int i = 0; i < clienteventBuffers.length; i++) {
			clienteventBuffers[i] = new byte[maxClientEventPacketSize];
		}
		// clientEventBuffer = ByteBuffer.allocate(pMaxClientPacketSize > 0 ? pMaxClientPacketSize + 1 : MAX_PACKETSIZE);
		clientInviteHandler = pClientInterface;
		clientEventHandler = pClientEventHandler;
		displayName = pMyDisplayName;
		needAliveConnection = pNeedAliveConnection;

		wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
		dhcp = wifiManager.getDhcpInfo();
		/*
		 * mlock = wifiManager.createMulticastLock(getPackageName()); mlock.setReferenceCounted(false); mlock.acquire();
		 */
		myIp = Formatter.formatIpAddress(dhcp.ipAddress);
		try {
			universalSocket = new DatagramSocket(null);
		} catch (SocketException e) {
		}
		isAlivePacket = new DatagramPacket(KEEPALIVE, KEEPALIVE.length);
		isAlivePacket.setPort(comm_port);
		clients = new Clients(this);
		discoveryListener = new Discovery();
		waitForInviteResponse = new ProgressDialog(activity);
		waitForInviteResponse.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		waitForInviteResponse.setMessage(activity.getString(R.string.client_waitforresponse));
		waitForInviteResponse.setCancelable(true);
		waitForInviteResponse.setCanceledOnTouchOutside(false);
		waitForInviteResponse.setOnCancelListener(discoveryListener);
		discoveryListener.execute((Void[]) null);
		if (!needAliveConnection) {
			clientEventListener = new ClientEventListener();
			clientEventListener.execute((Void[]) null);
		}
		sendDiscoveryRequest(displayName);
	}

	@Override
	public String[] onInviteAccept() {
		return null;
	}

	@Override
	public void onStartConnection(String[] data, int offset, final DatagramPacket pack) {
		// mlock.release();
		if (clientEventListener != null) clientEventListener.cancelme();
		clientEventListener = new ClientEventListener();
		clientEventListener.execute((Void[]) null);
		clientInviteHandler.onStartConnection(data, offset, pack);
	}

	@Override
	public void onClientAccepted(String[] data, int offset, DatagramPacket pack) {
		if (clientEventListener != null) clientEventListener.cancelme();
		clientEventListener = new ClientEventListener();
		clientEventListener.execute((Void[]) null);
		clientInviteHandler.onClientAccepted(data, offset, pack);
	}

	@Override
	public void onClientsEvent(final byte[] buffer, final DatagramPacket pack) {
		final InetAddress addr = pack.getAddress();
		if (needAliveConnection && buffer[0] != LanUDPComm.BUSY[0] && (connected || (invitedClient != null && !addr.equals(invitedClient)) || (invitingClient != null && !addr.equals(invitingClient)))) {
			sendPacket(new DatagramPacket(LanUDPComm.BUSY, LanUDPComm.BUSY.length, addr, LanUDPComm.discovery_port));
			return;
		}
		final String data = new String(buffer, 0, pack.getLength());
		if (data.startsWith(LanUDPComm.HI)) {
			final InetAddress sender = pack.getAddress();
			if (sender != null && !sender.getHostAddress().equalsIgnoreCase(LanUDPComm.myIp)) {
				if (clients.addItem(addr, data.trim()).equalsIgnoreCase(LanUDPComm.YES)) {
					final String info = LanUDPComm.HI + LanUDPComm.STRING_SPLIT_SEPARATOR + getDiscoveryDisplayName();
					sendPacket(new DatagramPacket(info.getBytes(), info.length(), sender, discovery_port));
				}
				activity.runOnUiThread(notifyDataSetsChanged);
			}
		} else if (needAliveConnection) {
			if (data.startsWith(LanUDPComm.NO)) {
				if (invitingClient != null && addr.equals(invitingClient)) {
					invitingClient = null;
					inviteDialog.dismiss();
					inviteDialog = null;
					activity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							new AlertDialog.Builder(activity).setCancelable(true).setIcon(drawable.ic_dialog_info).setMessage(activity.getString(R.string.client_cancelinvite, getClientName(addr))).setPositiveButton(string.ok, null).show().setOnDismissListener(dialogDismissListener);
						}
					});
				} else if (invitedClient != null && addr.equals(invitedClient)) {
					invitedClient = null;
					waitForInviteResponse.dismiss();
					activity.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							new AlertDialog.Builder(activity).setCancelable(true).setIcon(drawable.ic_dialog_info).setMessage(activity.getString(R.string.client_decline, getClientName(addr))).setPositiveButton(string.ok, null).show().setOnDismissListener(dialogDismissListener);
						}
					});
				}
			} else if (data.startsWith(LanUDPComm.YES)) {
				isAlivePacket.setAddress(addr);
				waitForInviteResponse.dismiss();
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						onClientAccepted(data.trim().split(LanUDPComm.STRING_SPLIT_SEPARATOR), 1, pack);
					}
				});
			} else if (data.startsWith(LanUDPComm.CLIENTINVITE)) {
				final String[] strData = data.trim().split(LanUDPComm.STRING_SPLIT_SEPARATOR);
				final String newName = strData[1];
				final String oldName = clients.addItem(addr, newName);
				if (!oldName.equalsIgnoreCase(LanUDPComm.YES) && !oldName.equalsIgnoreCase(LanUDPComm.NO)) {
					addMessage(oldName, activity.getString(R.string.client_changename, newName));
				}
				activity.runOnUiThread(new Runnable() {
					public void run() {
						invitingClient = addr;
						if (clientsAdapter != null) clientsAdapter.notifyDataSetChanged();
						if (messagesAdapter != null) messagesAdapter.notifyDataSetChanged();
						inviteDialog = new AlertDialog.Builder(activity).setCancelable(false).setIcon(drawable.ic_dialog_alert).setMessage(String.format(strData[0], strData[1])).setNegativeButton(string.cancel, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								sendPacket(new DatagramPacket(LanUDPComm.NO.getBytes(), LanUDPComm.NO.length(), invitingClient, LanUDPComm.discovery_port));
								inviteDialog = null;
								invitingClient = null;
							}
						}).setPositiveButton(string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								isAlivePacket.setAddress(invitingClient);
								onStartConnection(strData, 2, pack);
								final String request = buildPacketData(LanUDPComm.YES, null, clientInviteHandler.onInviteAccept());
								sendPacket(new DatagramPacket(request.getBytes(), request.length(), invitingClient, LanUDPComm.discovery_port));
								inviteDialog = null;
								invitingClient = null;
							}
						}).create();
						inviteDialog.setOnDismissListener(dialogDismissListener);
						inviteDialog.show();
					}
				});
			} else if (buffer[0] == LanUDPComm.BUSY[0]) {
				if (invitedClient != null && addr.equals(invitedClient)) {
					invitedClient = null;
					waitForInviteResponse.dismiss();
					activity.runOnUiThread(new Runnable() {
						public void run() {
							new AlertDialog.Builder(activity).setCancelable(true).setIcon(drawable.ic_dialog_info).setMessage(activity.getString(R.string.client_busy, getClientName(addr))).setPositiveButton(string.ok, null).show().setOnDismissListener(dialogDismissListener);
						}
					});
				}
			}
		} else {
			// not needAliveConnection, so only need to check for LanUDPComm.CLIENTINVITE and report it to application with clientInterface.onStartConnection(...)
			final String[] strData = data.trim().split(LanUDPComm.STRING_SPLIT_SEPARATOR);
			if (data.startsWith(LanUDPComm.CLIENTINVITE)) {
				final String newName = strData[0];
				final String oldName = clients.addItem(addr, newName);
				if (!oldName.equalsIgnoreCase(LanUDPComm.YES) && !oldName.equalsIgnoreCase(LanUDPComm.NO)) {
					activity.runOnUiThread(notifyDataSetsChanged);
				}
				clientInviteHandler.onStartConnection(strData, 1, pack);
				if (isAlivePacket.getAddress() != null && isAlivePacket.getAddress().equals(addr)) {
					final String request = buildPacketData(LanUDPComm.YES, null, clientInviteHandler.onInviteAccept());
					sendPacket(new DatagramPacket(request.getBytes(), request.length(), pack.getAddress(), LanUDPComm.discovery_port));
				}
			} else if (data.startsWith(LanUDPComm.YES)) {
				clientInviteHandler.onClientAccepted(strData, 1, pack);
			}
		}
		System.gc();
	}

	private final Runnable notifyDataSetsChanged = new Runnable() {
		@Override
		public void run() {
			if (clientsAdapter != null) clientsAdapter.notifyDataSetChanged();
			if (messagesAdapter != null) messagesAdapter.notifyDataSetChanged();
		}
	};

	/**
	 * Get a Time String from a timestamp in the form of {@link System#currentTimeMillis()} value.
	 * 
	 * @param timestamp
	 *            The long time value in the form of {@link System#currentTimeMillis()}.
	 * @param style
	 *            One of {@link #TIMESTRING_SHORT}, {@link #TIMESTRING_MEDIUM}, {@link #TIMESTRING_LONG} or {@link #TIMESTRING_FULL}.<br>
	 *            See {@link SimpleDateFormat} for information on these constants.
	 * @return A time String that is formatted according to {@code style} parameter.
	 */
	public final String getTimeString(final long timestamp, final int style) {
		return SimpleDateFormat.getTimeInstance(style).format(timestamp);
	}

	private final String getTimeString() {
		return "[" + SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(System.currentTimeMillis()) + "]";
	}

	private void addMessage(final String from, final String message) {
		final HashMap<String, String> map = new HashMap<String, String>();
		map.put(FROM_MESSAGES[0], getTimeString());
		map.put(FROM_MESSAGES[1], from);
		map.put(FROM_MESSAGES[2], message);
		this.messages.add(map);
	}

	private final String getDiscoveryDisplayName() {
		return (displayName != null ? displayName : myIp);
	}

	/**
	 * Helper for building standard response packet data. The data will start with the request, followed by message (only used with {@link #CLIENTINVITE}), then the sender's Display name (if {@code null}, ip address), and finally the extra data.
	 * 
	 * @param request
	 *            The request to send.
	 * @param message
	 *            The message to send, only needed for {@link #CLIENTINVITE}.
	 * @param extraData
	 *            The extra data (application specific) to be send along with the response. May be {@code null} if not needed.
	 * @return The data to be send.
	 */
	private final String buildPacketData(final String request, final String message, final String[] extraData) {
		String packetData = request + LanUDPComm.STRING_SPLIT_SEPARATOR;
		if (message != null) packetData += message + LanUDPComm.STRING_SPLIT_SEPARATOR;
		packetData += getDiscoveryDisplayName();
		if (extraData != null) {
			for (int i = 0; i < extraData.length; i++) {
				packetData += LanUDPComm.STRING_SPLIT_SEPARATOR + extraData[i];
			}
		}
		return packetData;
	}

	/**
	 * Calling this method will enable {@link LanUDPComm} to reflect changes in the list of clients back to your application.
	 * 
	 * @param pAdapter
	 *            The (custom) adapter, that has to be created by your application prior to calling this method.<br>
	 *            This can be a {@link SimpleAdapter} or any custom Adapter that you need for your application. If you are creating a SimpleAdapter, be sure to use {@link LanUDPComm#FROM_CLIENTS} as the String[] parameter in the Adapter's constructor, and use {@link #getClientsData()} to get the data needed for the {@code List<? extends Map<String, ?>>} parameter.
	 */
	public void setClientsAdapter(BaseAdapter pAdapter) {
		clientsAdapter = pAdapter;
	}

	/**
	 * Calling this method will enable {@link LanUDPComm} to reflect changes in the list of messages back to your application.
	 * 
	 * @param context
	 *            The application context.
	 * @param layoutResId
	 *            The Layout resource id for a single ListView item.
	 * @param to
	 *            An int array, consisting of a single entry, that holds the id of a TextView within the Layout resource to display the client name.
	 */
	public BaseAdapter getMessagesAdapter(Context context, final int layoutResId, final int[] to) {
		messagesAdapter = new SimpleAdapter(context, messages, layoutResId, FROM_MESSAGES, to);
		return messagesAdapter;
	}

	/**
	 * Invites a client for a connection. After the target client has accepted the invite, all subsequent calls to {@link #sendClientPacket()} and {@link #sendClientPacket(String)} will target this client.
	 * 
	 * @param index
	 *            The index into the clientlist adapter, previously instantiated with {@link #setClientsAdapter(BaseAdapter)}.
	 * @param message
	 *            The message to display to the target. This String should contain one {@literal %s} or {@literal %1$s}, that will hold the name of the inviting client. If {@code null}, it will fallback on a default message String that is included in {@link LanUDPComm}.
	 * @param extraData
	 *            An array of Strings to send along with the invitation. This data will be received in {@link #onStartConnection(String[], int, DatagramPacket)}. May be {@code null} if not needed.
	 */
	public void inviteClientForConnection(final int index, final String message, final String[] extraData) {
		if (index >= 0 && index < clients.size()) {
			if (needAliveConnection) {
				if (!connected) {
					if (invitedClient == null) {
						invitedClient = clients.getInetAddress(index);
						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								waitForInviteResponse.show();
							}
						});
						final String msg;
						if (message == null) msg = activity.getString(R.string.client_invite);
						else msg = message;
						String invite = buildPacketData(LanUDPComm.CLIENTINVITE, msg, extraData);
						final DatagramPacket pack = new DatagramPacket(invite.getBytes(), invite.length(), invitedClient, LanUDPComm.discovery_port);
						sendPacket(pack);
					}
				}
			} else {
				isAlivePacket.setAddress(clients.getInetAddress(index));
				String invite = buildPacketData(LanUDPComm.CLIENTINVITE, null, extraData);
				final DatagramPacket pack = new DatagramPacket(invite.getBytes(), invite.length(), clients.getInetAddress(index), LanUDPComm.discovery_port);
				sendPacket(pack);
			}
		}
	}

	private void sendPacket(final DatagramPacket pack) {
		try {
			universalSocket.setBroadcast(false);
			universalSocket.setReuseAddress(true);
			universalSocket.send(pack);
		} catch (Exception e) {
		}
	}

	private InetAddress getBroadcastAddress() {
		final int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		final byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++) {
			quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		}
		try {
			return InetAddress.getByAddress(quads);
		} catch (UnknownHostException e) {
		}
		return null;
	}

	private byte[] getBroadcastIp() {
		final int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		final byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++) {
			quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		}
		return quads;
	}

	/**
	 * Send a discovery request, optionally wih a Display Name, making yourself known to other devices. If this client's display name changes during the life cycle of you application, you should use this method again, to let other clients know the new display name.
	 * 
	 * @param pDisplayName
	 *            the (new) Display Name to send along with the Discovery request. If {@code null}, it will send this client's IP address.
	 */
	public void sendDiscoveryRequest(final String pDisplayName) {
		displayName = pDisplayName;
		final String request = LanUDPComm.HI + LanUDPComm.STRING_SPLIT_SEPARATOR + getDiscoveryDisplayName();
		try {
			final DatagramPacket broadcast = new DatagramPacket(request.getBytes(), request.length(), getBroadcastAddress(), discovery_port);
			universalSocket.setReuseAddress(true);
			universalSocket.setBroadcast(true);
			universalSocket.send(broadcast);
		} catch (IOException e) {
		}
	}

	public void sendDiscoveryToAllIps(final String pDisplayName) {
		displayName = pDisplayName;
		final String request = LanUDPComm.HI + LanUDPComm.STRING_SPLIT_SEPARATOR + getDiscoveryDisplayName();
		final byte[] iprange = getBroadcastIp();
		final DatagramPacket broadcast = new DatagramPacket(request.getBytes(), request.length());
		broadcast.setPort(discovery_port);
		iprange[3] = -128;
		while (iprange[3] < 127) {
			iprange[3]++;
			try {
				broadcast.setAddress(InetAddress.getByAddress(iprange));
				universalSocket.setReuseAddress(true);
				universalSocket.setBroadcast(false);
				universalSocket.send(broadcast);
			} catch (Exception e) {
			}
		}
	}

	public void sendDiscoveryByeBye() {
		try {
			final String request = LanUDPComm.BYE + LanUDPComm.STRING_SPLIT_SEPARATOR + getDiscoveryDisplayName();
			final DatagramPacket broadcast = new DatagramPacket(request.getBytes(), request.length(), getBroadcastAddress(), discovery_port);
			universalSocket.setReuseAddress(true);
			universalSocket.setBroadcast(true);
			universalSocket.send(broadcast);
			return;
		} catch (IOException e) {
		}
	}

	/**
	 * Get the {@link ByteBuffer} to use for storing binary data. The first byte is set to {@link #CLIENTEVENT} for internal usage, as stated in the - NOTICE - of {@link LanUDPComm}. If this byte is changed, the data will not be recognized at the recipient side, and as such will not be handled correctly: the {@link ClientEventHandler#onClientEvent(byte[], int, int, DatagramPacket)} will not get called.<br>
	 * <br>
	 * CAUTION: It is your own responsibility not to exceed the {@link ByteBuffer} limit ({@code pMaxClientPacketSize} you supplied when instantiating {@link LanUDPComm}), or your application will crash if you do not catch the exception.
	 * 
	 * @return {@link ByteBuffer}
	 */
	public ByteBuffer GetClientEventBuffer() {
		clientEventBuffer = ByteBuffer.wrap(clienteventBuffers[clienteventIndex]);
		// clientEventBuffer.position(0);
		clientEventBuffer.put(LanUDPComm.CLIENTEVENT);
		clienteventIndex++;
		if (clienteventIndex == clienteventBuffers.length) clienteventIndex = 0;
		return clientEventBuffer;
	}

	/**
	 * This is used for sending binary data to a client.<br>
	 * First call {@link #GetClientEventBuffer} to get a handle to an already created {@link ByteBuffer} with a maximum size of the {@code pMaxClientPacketSize} parameter that was supplied to the LanCommander constructor. With this {@link ByteBuffer} you can store your data in a simple way with existing methods such as {@link ByteBuffer#putFloat(float)} etcetera. When you are done storing the data, call this method to send the data.<br>
	 * <br>
	 * CAUTION: It is your own responsibility not to exceed the {@link ByteBuffer} limit ({@code pMaxClientPacketSize} you supplied when instantiating {@link LanUDPComm}), or your application will crash if you do not catch the exception.
	 */
	public void sendClientPacket() {
		if (connected) {
			try {
				universalSocket.setBroadcast(false);
				universalSocket.setReuseAddress(true);
				universalSocket.send(new DatagramPacket(clientEventBuffer.array(), clientEventBuffer.position(), isAlivePacket.getAddress(), comm_port));
			} catch (Exception e) {
			}
		}
	}

	/**
	 * This is used for sending {@link String} data to a target.
	 * 
	 * @param data
	 *            The data to be send.
	 */
	public void sendClientPacket(final String data) {
		if (connected) {
			try {
				universalSocket.setBroadcast(false);
				universalSocket.setReuseAddress(true);
				GetClientEventBuffer();
				clientEventBuffer.put(data.getBytes());
				universalSocket.send(new DatagramPacket(clientEventBuffer.array(), clientEventBuffer.position(), isAlivePacket.getAddress(), comm_port));
			} catch (Exception e) {
			}
		}
	}

	/**
	 * Call this if you don't have a {@link #needAliveConnection} and you want to let the target client know that you are ending the connection. This is usefull for example for chat-like applications, so that {@link ClientEventHandler#onClientEndConnection(DatagramPacket)} gets called and you can show client status on the receiving end.
	 */
	public void sendClientBye() {
		if (isAlivePacket.getAddress() != null) {
			sendPacket(new DatagramPacket(LanUDPComm.BYE.getBytes(), LanUDPComm.BYE.length(), isAlivePacket.getAddress(), comm_port));
			isAlivePacket.setAddress(null);
		}
	}

	/**
	 * Call this if you have a {@link #needAliveConnection} and you want to let the target know that you are ending the connection. If this is not needed for your application, then you do not have to call this.
	 * 
	 * @return {@code true} if the connection was actually stopped, {@code false} if it was already stopped.
	 */
	public boolean stopConnection() {
		invitedClient = null;
		invitingClient = null;
		// mlock.acquire();
		if (connected) {
			connected = false;
			if (clientEventListener != null) clientEventListener.cancelme();
			sendClientBye();
			return true;
		}
		return false;
	}

	public final List<Map<String, String>> getClientsData() {
		return clients.getClientsAdapterData();
	}

	public final int getClientPositionFromIP(final InetAddress ip) {
		return clients.getPositionFromIP(ip);
	}

	public final String getClientName(InetAddress addr) {
		return clients.getClientName(addr);
	}

	public final String getClientName(final int position) {
		return clients.getClientName(position);
	}

	/**
	 * Gets the {@link InetAddress} of the client at a specific position in the list.
	 * 
	 * @param position
	 *            The position in the clients list to return information about.
	 * @return The InetAddress of the client at the specified position. Will return {@code null} if the position can not be accessed.
	 */
	public InetAddress getClientIPfromPosition(final int position) {
		return clients.getInetAddress(position);
	}

	/**
	 * Gets the {@code Map<String, String>} information of the client at a specific position in the list.
	 * 
	 * @param position
	 *            The position in the clients list to return information about.
	 * @return The {@code Map<String, String>} of the client at the specified position. Will return {@code null} if the position can not be accessed.
	 */
	public final Map<String, String> getClientsItemAtPosition(final int position) {
		return clients.get(position);
	}

	/**
	 * @return The number of clients in the clients list.
	 */
	public int getNumClients() {
		return clients.size();
	}

	private class ClientEventListener extends AsyncTask<Void, Void, Void> {
		private static final int KEEPALIVE_INTERVAL = 1000;
		private final byte[] buffer = new byte[maxClientEventPacketSize];
		private final DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
		private DatagramSocket clientSocket = null;
		private boolean running = true;
		private long clientTTL;
		private Handler mHandler = new Handler();

		@Override
		protected void onPreExecute() {
			try {
				clientSocket = new DatagramSocket(null);
				clientSocket.setBroadcast(false);
				clientSocket.setReuseAddress(true);
				clientSocket.setSoTimeout(0);
				clientSocket.bind(new InetSocketAddress(LanUDPComm.myIp, comm_port));
				connected = true;
			} catch (IOException e) {
				cancel(true);
				addMessage(activity.getString(R.string.error_lan_nocomm), "");
				activity.runOnUiThread(notifyDataSetsChanged);
			}
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(Void... params) {
			if (clientSocket != null) {
				if (needAliveConnection) mHandler.postDelayed(keepConnectionAlive, KEEPALIVE_INTERVAL);
				while (running && !isCancelled()) {
					try {
						if (needAliveConnection) clientTTL = System.currentTimeMillis() + KEEPALIVE_INTERVAL * 2;
						clientSocket.receive(pack);
						onClientEvent(buffer, 1, pack.getLength(), pack);
					} catch (IOException e) {
					}
				}
			}
			stop();
			return null;
		}

		@Override
		protected void onCancelled() {
			stop();
			super.onCancelled();
		}

		public void cancelme() {
			if (!clientEventListener.isCancelled()) {
				clientEventListener.cancel(true);
			}
		}

		private void stop() {
			running = false;
			if (clientSocket != null) {
				clientSocket.close();
				clientSocket = null;
			}
			stopConnection();
		}

		private void sendAlivePacket() {
			try {
				universalSocket.setBroadcast(false);
				universalSocket.setReuseAddress(true);
				universalSocket.send(isAlivePacket);
			} catch (Exception e) {
			}
		}

		private Runnable keepConnectionAlive = new Runnable() {
			@Override
			public void run() {
				if (running) {
					if (clientTTL > System.currentTimeMillis()) {
						sendAlivePacket();
						mHandler.postDelayed(this, KEEPALIVE_INTERVAL);
					} else {
						running = false;
						clientEventHandler.onClientNotResponding(pack);
						cancel(true);
						stopConnection();
					}
				}
			}
		};
	}

	@Override
	public void onClientEvent(final byte[] data, final int offset, final int dataLength, final DatagramPacket pack) {
		if (!connected) return;
		if (data[0] == LanUDPComm.CLIENTEVENT) {
			clientEventHandler.onClientEvent(data, offset, dataLength, pack);
		} else if (data[0] == LanUDPComm.BYEBYTE) {
			if (needAliveConnection) {
				if (clientEventListener != null) clientEventListener.cancelme();
				clientEventHandler.onClientEndConnection(pack);
				stopConnection();
			} else {
				clientEventHandler.onClientEndConnection(pack);
			}
		}
	}

	@Override
	public void onClientEndConnection(final DatagramPacket pack) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (waitForInviteResponse.isShowing()) waitForInviteResponse.dismiss();
				new AlertDialog.Builder(activity).setCancelable(false).setIcon(drawable.ic_dialog_info).setMessage(activity.getString(R.string.client_endconnection, getClientName(pack.getAddress()))).setPositiveButton(string.ok, null).show().setOnDismissListener(dialogDismissListener);
			}
		});
	}

	@Override
	public void onClientNotResponding(final DatagramPacket pack) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (waitForInviteResponse.isShowing()) waitForInviteResponse.dismiss();
				new AlertDialog.Builder(activity).setCancelable(false).setIcon(drawable.ic_dialog_info).setMessage(activity.getString(R.string.client_notresponding, getClientName(pack.getAddress()))).setPositiveButton(string.ok, null).show().setOnDismissListener(dialogDismissListener);
			}
		});
	}

	private class Discovery extends AsyncTask<Void, Void, Void> implements DialogInterface.OnCancelListener {
		private final byte[] buffer = new byte[LanUDPComm.MAX_PACKETSIZE];
		private boolean running = true;
		private DatagramSocket discoverySocket = null;

		@Override
		protected void onPreExecute() {
			try {
				discoverySocket = new DatagramSocket(null);
				discoverySocket.setReuseAddress(true);
				discoverySocket.setBroadcast(true);
				discoverySocket.setSoTimeout(0);
				discoverySocket.bind(new InetSocketAddress(getBroadcastAddress(), discovery_port));
			} catch (IOException e) {
				cancel(true);
				addMessage(activity.getString(R.string.error_lan_nocomm), "");
				activity.runOnUiThread(notifyDataSetsChanged);
			}
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(Void... params) {
			final DatagramPacket pack = new DatagramPacket(buffer, buffer.length);
			if (discoverySocket != null) {
				while (running && !isCancelled()) {
					try {
						discoverySocket.receive(pack);
						final InetAddress sender = pack.getAddress();
						if (LanUDPComm.myIp == null || (sender != null && !sender.getHostAddress().equalsIgnoreCase(LanUDPComm.myIp))) {
							final String data = new String(buffer, 0, pack.getLength());
							if (data.startsWith(LanUDPComm.BYE)) {
								final String name = data.trim();
								clients.removeItem(sender);
								addMessage(name, activity.getString(R.string.client_offline));
								activity.runOnUiThread(notifyDataSetsChanged);
							} else if (data.startsWith(LanUDPComm.HI)) {
								final String newName = data.trim();
								final String oldName = clients.addItem(sender, newName);
								final String info = LanUDPComm.HI + LanUDPComm.STRING_SPLIT_SEPARATOR + getDiscoveryDisplayName();
								sendPacket(new DatagramPacket(info.getBytes(), info.length(), sender, discovery_port));
								if (!oldName.equals(LanUDPComm.NO)) {
									if (oldName.equals(LanUDPComm.YES)) {
										addMessage(newName, activity.getString(R.string.client_online));
									} else {
										addMessage(oldName, activity.getString(R.string.client_changename, newName));
									}
									activity.runOnUiThread(notifyDataSetsChanged);
								}
							}
						}
					} catch (InterruptedIOException e) {
					} catch (IOException e) {
					}
				}
			}
			return null;
		}

		@Override
		protected void onCancelled() {
			running = false;
			discoverySocket.close();
			discoverySocket = null;
			super.onCancelled();
		}

		@Override
		public void onCancel(DialogInterface dialog) {
			sendPacket(new DatagramPacket(LanUDPComm.NO.getBytes(), LanUDPComm.NO.length(), invitedClient, discovery_port));
			invitedClient = null;
		}
	}

	/**
	 * @return Returns whether this client is currently connected to a client. If {@link #needAliveConnection} is {@code false}, this method will always return {@code true} because then a connection is not required to be able to send data to other clients.
	 */
	public boolean isConnected() {
		return connected;
	}

	/**
	 * Use this when your application exits and you need to let other clients know that this client is no longer available. If this is not needed for your application, then you do not have to call this.
	 */
	public void cleanup() {
		stopConnection();
		if (clients != null && !clients.isCancelled()) clients.cancel(true);
		if (clientEventListener != null) clientEventListener.cancelme();
		if (discoveryListener != null && !discoveryListener.isCancelled()) discoveryListener.cancel(true);
		sendDiscoveryByeBye();
	}
}
