package com.larphoid.lanudpcomm;

import java.net.DatagramPacket;

/**
 * © 2013 Larphoid Apps.
 * 
 * @since 05-09-2013
 * @author Larphoid Apps<br>
 * <br>
 *         <b>This interface is used to report client requests, such as invites, back to your application.</b>
 */
public interface ClientInviteHandler {
	/**
	 * Called when this client accepts an invite request, providing a way to send application specific data along with the accept response.
	 * 
	 * @return An array of Strings as data to send along with the invite response back to the inviting client. May be {@code null} if not needed.
	 */
	String[] onInviteAccept();

	/**
	 * Called at the target side, when the target has accepted an invite, to signal the start of the connection.<br>
	 * If {@link LanUDPComm#needAliveConnection} is {@code false}, this will always get called without the target having to accept, so that the application can keep track of client status.
	 * 
	 * @param data
	 *            The String array with your application specific data that was specified in {@link #onInviteAccept()}.
	 * @param offset
	 *            The offset into the Sting array where your application specific data starts.
	 * @param pack
	 *            This is supplied for convenience reasons. It allows the application to access additional information stored in the {@link DatagramPacket} about a connection, such as {@link DatagramPacket#getAddress()}.
	 */
	void onStartConnection(final String[] data, final int offset, final DatagramPacket pack);

	/**
	 * Called at the inviting client side, when the target has accepted an invite, to signal the start of the connection.<br>
	 * If {@link LanUDPComm#needAliveConnection} is {@code false}, this will always get called if the target's last {@link LanUDPComm#inviteClientForConnection(int, String, String[])} ip equals the inviting client's ip, so that the application can keep track of client status.
	 * 
	 * @param data
	 *            The String array with your application specific data that was specified in {@link #onInviteAccept()}.
	 * @param offset
	 *            The offset into the Sting array where your application specific data starts.
	 * @param pack
	 *            This is supplied for convenience reasons. It allows the application to access additional information stored in the {@link DatagramPacket} about a connection, such as {@link DatagramPacket#getAddress()}.
	 */
	void onClientAccepted(final String[] data, final int offset, final DatagramPacket pack);
}
