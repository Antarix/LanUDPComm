package com.larphoid.lanudpcomm;

import java.net.DatagramPacket;

/**
 * (c) 2013 Ralph Lussenburg.
 * 
 * @author Ralph Lussenburg
 * @since 05-09-2013
 */
public interface ClientsEventHandler {
	/**
	 * Fired when a client sends an invite or other events related to Discovery. Used internally by LanCommander only.
	 * 
	 * @param buffer
	 * @param pack
	 */
	void onClientsEvent(final byte[] buffer, final DatagramPacket pack);

}
