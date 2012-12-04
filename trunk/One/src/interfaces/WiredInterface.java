package interfaces;

import java.util.Collection;

import core.CBRConnection;
import core.Connection;
import core.DTNHost;
import core.ModuleCommunicationBus;
import core.NetworkInterface;
import core.Settings;

public class WiredInterface extends NetworkInterface {
	/**
	 * Reads the interface settings from the Settings file
	 * 
	 */
	private boolean checkCreateConnection = false;

	public WiredInterface(Settings s) {
		super(s);
	}

	/**
	 * Copy constructor
	 * 
	 * @param ni
	 *            the copied network interface object
	 */
	public WiredInterface(WiredInterface ni) {
		super(ni);
	}

	public void setHost(DTNHost host) {
		this.host = host;
		ModuleCommunicationBus comBus = host.getComBus();
		comBus.subscribe(SCAN_INTERVAL_ID, this);
		comBus.subscribe(RANGE_ID, this);
		comBus.subscribe(SPEED_ID, this);

		optimizer = ConnectivityGrid.ConnectivityGridFactory(this.interfacetype
				.hashCode(), transmitRange);
		optimizer.addInterface(this);
	}

	@Override
	/*
	 * * Tries to connect this host to another host. The other host must be
	 * active. Because it is wired connection, so it not check the range
	 * 
	 * @param anotherInterface The interface to connect to
	 */
	public void connect(NetworkInterface anotherInterface) {
		// TODO Auto-generated method stub
		if (isScanning() && anotherInterface.getHost().isActive()
		// && isWithinRange(anotherInterface)
				// check list of connection here
				// try to do not check range, because it is Wired connection.
				&& !isConnected(anotherInterface) && (this != anotherInterface)) {
			// new contact within range
			// connection speed is the lower one of the two speeds
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed;
			}

			Connection con = new CBRConnection(this.host, this,
					anotherInterface.getHost(), anotherInterface, conSpeed);
			
			connect(con, anotherInterface);
		}
	}

	@Override
	/*
	 * * Creates a connection to another host. This method does not do any
	 * checks on whether the other node is in range or active
	 * 
	 * @param anotherInterface The interface to create the connection to
	 */
	public void createConnection(NetworkInterface anotherInterface) {
		// TODO Auto-generated method stub
		if (!isConnected(anotherInterface) && (this != anotherInterface)) {
			// connection speed is the lower one of the two speeds
			int conSpeed = anotherInterface.getTransmitSpeed();
			if (conSpeed > this.transmitSpeed) {
				conSpeed = this.transmitSpeed;
			}

			Connection con = new CBRConnection(this.host, this,
					anotherInterface.getHost(), anotherInterface, conSpeed);
			connect(con, anotherInterface);
		}
	}

	@Override
	public NetworkInterface replicate() {
		// TODO Auto-generated method stub
		return new WiredInterface(this);
	}

	@Override
	/*
	 * * Updates the state of current connections (ie tears down connections
	 * that are out of range).
	 */
	public void update() {
		// TODO Auto-generated method stub
		// Make new connection if it has not maked
		if (!checkCreateConnection) {
			Collection<NetworkInterface> interfaces = optimizer
					.getAllInterfaces();
			for (NetworkInterface i : interfaces) {
				connect(i);
			}
			checkCreateConnection = true;
		}
	}

}
