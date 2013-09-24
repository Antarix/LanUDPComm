package com.larphoid.lanudpcomm;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

/**
 * © 2013 Larphoid Apps.
 * 
 * @since 05-09-2013
 * @author Ralph Lussenburg<br>
 * <br>
 *         <b>This interface is used to report received data and client disconnecting, back to your application.</b>
 */
public interface ClientEventHandler {
	/**
	 * Callback that receives data that was send either with {@link LanUDPComm#sendClientPacket()} or {@link LanUDPComm#sendClientPacket(String)} for a connection that was previously established with {@link LanUDPComm#inviteClientForConnection(int, String, String[])}.<br>
	 * <br>
	 * There are two methods to use for communication. One for 'fast' binary data, and one for simple {@linkplain String} data. It is 'fast' because if there are a lot of events and you need to send data other than String data, it can be very slow if you first have to convert it into a String.<br>
	 * 
	 * @see <a> For information on how to use data communication, see {@link LanUDPComm#sendClientPacket()} and {@link LanUDPComm#sendClientPacket(String data)}. </a>
	 * @param data
	 *            A byte array which holds your application specific data, to use for communication between the 2 clients. To simply convert to a String, use: {@code new String(data, offset, dataLength - offset)}</code>.
	 * @param offset
	 *            The offset of the data in the byte array. You can use {@linkplain ByteBuffer} to wrap the byte array and {@code ByteBuffer.position(offset)} to advance the byte array to this position.
	 * @param dataLength
	 *            The length of the data that is stored in the byte[] array.
	 * @param pack
	 *            Included for convenience reasons. The {@link DatagramPacket} holds all the information about the data that was received.
	 */
	void onClientEvent(final byte[] data, final int offset, final int dataLength, final DatagramPacket pack);

	/**
	 * Called when a client ends the connection.<br>
	 * This is usefull for example for games to end the game state, or for chat-like applications to keep track of client status. If not needed, simply do nothing in this callback method.<br>
	 * If you want a dialog displayed to inform this client that the target has ended the connection, call {@code yourLanUDPCommInstance.onClientEndConnection(DatagramPacket)}.
	 */
	void onClientEndConnection(final DatagramPacket pack);

	/**
	 * Called (if you have a {@link LanUDPComm#needAliveConnection} when a client is not responding anymore (no longer sending 'keepalive' packets).<br>
	 * This is usefull for example for games to end the game state. If not needed, simply do nothing in this callback method.<br>
	 * If you want a dialog displayed to inform this client that the target is not responding anymore, call {@code yourLanUDPCommInstance.onClientNotResponding(DatagramPacket)}.
	 */
	void onClientNotResponding(final DatagramPacket pack);
}
