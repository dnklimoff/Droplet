/*******************************************************************************
 * Project: Droplet - Toolkit for Liquid Art Photographers
 * Copyright (C) 2012 Stefan Brenner
 *
 * This file is part of Droplet.
 *
 * Droplet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Droplet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Droplet. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.stefanbrenner.droplet.service.impl;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TooManyListenersException;

import org.apache.commons.lang3.StringUtils;
import org.mangosdk.spi.ProviderFor;

import com.stefanbrenner.droplet.model.IDropletContext;
import com.stefanbrenner.droplet.service.ISerialCommunicationService;

/**
 * <p>
 * Service for serial communication with an Arduino microcontroller.
 * <p>
 * For more information on Arduino see {@link http://arduino.cc}
 * 
 * @author Stefan Brenner
 */
@ProviderFor(ISerialCommunicationService.class)
public class ArduinoService implements ISerialCommunicationService, SerialPortEventListener {

	/** Milliseconds to block while waiting for port open */
	private static final int TIME_OUT = 2000;
	/** Default bits per second for COM port */
	private static final int DATA_RATE = 9600;

	/** connected port information **/
	private static CommPortIdentifier connPortId = null;
	private static SerialPort connSerialPort = null;

	/** input stream for sending data **/
	private static InputStream input = null;
	/** output streams for receiving data **/
	private static OutputStream output = null;

	/** flag that indicates if the service is currently connected to a port **/
	private static boolean connected = false;

	private static IDropletContext dropletContext;

	@Override
	public String getName() {
		return "Arduino Service";
	}

	@Override
	public CommPortIdentifier[] getPorts() {
		List<CommPortIdentifier> ports = new ArrayList<CommPortIdentifier>();

		Enumeration<?> portEnum = CommPortIdentifier.getPortIdentifiers();
		// iterate through, looking for the port
		while (portEnum.hasMoreElements()) {
			CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
			// add only serial ports
			if (currPortId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				ports.add(currPortId);
			}
		}

		return ports.toArray(new CommPortIdentifier[] {});
	}

	@Override
	public synchronized boolean connect(CommPortIdentifier portId, IDropletContext context) {
		try {
			connPortId = portId;
			dropletContext = context;
			// try to open a connection to the serial port
			connSerialPort = (SerialPort) portId.open(this.getClass().getName(), TIME_OUT);

			// set port parameters
			connSerialPort.setSerialPortParams(DATA_RATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);

			// open the streams
			input = connSerialPort.getInputStream();
			output = connSerialPort.getOutputStream();

			// add event listeners
			connSerialPort.addEventListener(this);
			connSerialPort.notifyOnDataAvailable(true);
			connSerialPort.notifyOnCarrierDetect(true);

			// set connected flag
			setConnected(true);

			return true;

		} catch (PortInUseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedCommOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TooManyListenersException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		setConnected(false);
		return false;
	}

	@Override
	public synchronized void close() {
		try {
			if (connSerialPort != null) {
				connSerialPort.removeEventListener();
				connSerialPort.close();
				input.close();
				output.close();
				setConnected(false);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void serialEvent(SerialPortEvent event) {
		if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			int data;
			byte[] buffer = new byte[1024];
			try {
				int len = 0;
				while ((data = input.read()) > -1) {
					if (data == '\n') {
						break;
					}
					buffer[len++] = (byte) data;
				}

				// TODO brenner: integrate message protocol to parse result
				// message

				// add message to model
				String message = new String(buffer, 0, len);
				dropletContext.addLoggingMessage(message);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// TODO brenner: implement a two way synchronous communication protocol
	@Override
	public void sendData(String message) {
		try {
			if (output != null) {

				// split message by newline
				for (String msg : StringUtils.split(message, '\n')) {

					// send byte by byte
					for (byte b : msg.getBytes()) {
						output.write(b);
						output.flush();
					}

					// finally send newline
					output.write('\n');
					output.flush();

					// wait for Arduino to process input before we send next
					// message
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}

				}

				// remove control characters
				// message = StringUtils.trim(message);
				// output.write(message.getBytes());
				// append newline
				// if (!message.endsWith("\n")) {
				// output.write('\n');
				// }
				// output.flush();
			} else {
				throw new RuntimeException("Not connected to a port!"); //$NON-NLS-1$
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public synchronized boolean isConnected() {
		return connected;
	}

	private void setConnected(boolean connected) {
		ArduinoService.connected = connected;
	}

}