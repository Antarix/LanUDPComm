package com.larphoid.lanudpcomm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.AsyncTask;

/**
 * © 2013 Larphoid Apps.
 * 
 * @author Ralph Lussenburg
 * @since 05-09-2013
 */

public class Clients extends AsyncTask<String, Integer, String> {
	private ClientsEventHandler eventHandler;
	private List<InetAddress> ip = new ArrayList<InetAddress>();
	private List<String> name = new ArrayList<String>();
	private List<Map<String, String>> data = new ArrayList<Map<String, String>>();
	private boolean running = true;
	private DatagramSocket socket = null;
	private final byte[] buffer = new byte[LanUDPComm.MAX_PACKETSIZE];
	private final DatagramPacket pack = new DatagramPacket(buffer, buffer.length);

	public Clients(ClientsEventHandler pClientsEventHandler) {
		eventHandler = pClientsEventHandler;
		execute((String[]) null);
	}

	@Override
	protected void onPreExecute() {
		try {
			socket = new DatagramSocket(null);
			socket.setBroadcast(false);
			socket.setReuseAddress(true);
			socket.setSoTimeout(0);
			socket.bind(new InetSocketAddress(LanUDPComm.myIp, LanUDPComm.discovery_port));
			running = true;
		} catch (IOException e) {
			running = false;
		}
		super.onPreExecute();
	}

	@Override
	protected String doInBackground(String... params) {
		if (socket != null) {
			while (running && !isCancelled()) {
				try {
					socket.receive(pack);
					eventHandler.onClientsEvent(buffer, pack);
				} catch (IOException e) {
				}
			}
		} else {
			running = false;
		}
		onCancelled();
		return null;
	}

	@Override
	protected void onCancelled() {
		running = false;
		if (socket != null) {
			socket.close();
			socket = null;
		}
		super.onCancelled();
	}

	public String addItem(InetAddress addr, String name) {
		final int index = this.ip.indexOf(addr);
		if (index == -1) {
			this.ip.add(addr);
			this.name.add(name);
			final Map<String, String> item = new HashMap<String, String>();
			item.put(LanUDPComm.FROM_CLIENTS[0], name);
			this.data.add(item);
			return LanUDPComm.YES;
		} else {
			final String old = this.name.get(index);
			this.name.set(index, name);
			final Map<String, String> item = new HashMap<String, String>();
			item.put(LanUDPComm.FROM_CLIENTS[0], name);
			this.data.set(index, item);
			if (!old.equalsIgnoreCase(name)) return old;
		}
		return LanUDPComm.NO;
	}

	private boolean removeItem(final int index) {
		if (index >= 0 && index < this.ip.size()) {
			this.ip.remove(index);
			this.name.remove(index);
			this.data.remove(index);
			return true;
		}
		return false;
	}

	public boolean removeItem(final InetAddress addr) {
		return removeItem(this.ip.indexOf(addr));
	}

	public final List<Map<String, String>> getClientsAdapterData() {
		return this.data;
	}

	public String getClientName(InetAddress addr) {
		final int index = this.ip.indexOf(addr);
		if (index != -1) return this.name.get(index);
		return null;
	}

	public String getClientName(final int position) {
		if (position >= 0 || position < this.name.size()) return this.name.get(position);
		return null;
	}

	public final int getPositionFromIP(InetAddress addr) {
		return this.ip.indexOf(addr);
	}

	public final int size() {
		return this.ip.size();
	}

	public final InetAddress getInetAddress(final int position) {
		if (position >= 0 && position < this.ip.size()) return this.ip.get(position);
		return null;
	}

	public final Map<String, String> get(final int position) {
		if (position >= 0 && position < this.data.size()) return this.data.get(position);
		return null;
	}
}
