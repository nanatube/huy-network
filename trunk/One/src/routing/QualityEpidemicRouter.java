/*
 * SimpleRouter.java
 *
 * Copyright 2011 by Matthew Orlinski, released under GPLv3.
 * Simple Router logic adapted from code by PJ Dillon http://www.cs.pitt.edu/~pdillon/one/ also GPLv3
 * 
 */

package routing;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;


import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.Tuple;


public class QualityEpidemicRouter extends ActiveRouter {
	
	/** Prophet router's setting namespace ({@value})*/ 
	public static final String SIMPLE_NS = "QualityEpidemic";

	/** Threshold value for adding a host to the local community -setting id 
	 * {@value} 
	 */
	public static final String LAMBDA_SETTING = "lambda";
	
	/** Threshold value for merging the local community with a peer -setting id 
	 * {@value} 
	 */
	public static final String GAMMA_SETTING = "gamma";
	
	/** Total contact time threshold for adding a node to the familiar set 
	 * -setting id {@value} 
	 */
	public static final String FAMILIAR_SETTING = "familiarThreshold";
	public static final String DEGRADE = "degrade";
	
// current working variables
	public Map<DTNHost, Double> neighbourSet;
	public Set<DTNHost> localCommunity;

	protected double lambda;
	protected double gamma;
	protected double familiarThreshold;

	
	public QualityEpidemicRouter(Settings s) {
		super(s);
		Settings simpleSettings = new Settings(SIMPLE_NS);
		this.lambda = simpleSettings.getDouble(LAMBDA_SETTING);
		this.gamma = simpleSettings.getDouble(GAMMA_SETTING);
		this.familiarThreshold = simpleSettings.getDouble(FAMILIAR_SETTING);
	}

	public QualityEpidemicRouter(QualityEpidemicRouter proto) {
		super(proto);
		this.lambda = proto.lambda;
		this.gamma = proto.gamma;
		this.familiarThreshold = proto.familiarThreshold;

		neighbourSet = new HashMap<DTNHost, Double>();
		localCommunity = new HashSet<DTNHost>();
		
	}

	@Override
	public QualityEpidemicRouter replicate() {
		return new QualityEpidemicRouter(this);
	}
	
	private boolean isMostConnected(DTNHost peer) {
		// loop over all neighbours
		Iterator<Entry<DTNHost, Double>> it = this.neighbourSet.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pairs = it.next();
			if((Double) pairs.getValue() > this.neighbourSet.get(peer))
				return false;
		}
		return true;
	}
	
	
	@Override
	public void changedConnection(Connection con)
	{
	
		DTNHost myHost = getHost();
		DTNHost otherNode = con.getOtherNode(myHost);
		QualityEpidemicRouter otherRouter = (QualityEpidemicRouter)otherNode.getRouter();
		
		if(con.isUp())
		{
			if(this.neighbourSet.containsKey(otherNode)) {
				this.neighbourSet.put(otherNode, this.neighbourSet.get(otherNode) + 1);
			}
			else
				this.neighbourSet.put(otherNode, 1.0);
			
			
			otherRouter.localCommunity.add(otherNode);
			
			// check local community information with new connections
			checkLocalCommunity(con);

		}

	}
	
	public void checkLocalCommunity(Connection con) {
	
		DTNHost peer = con.getOtherNode(getHost());
		QualityEpidemicRouter peerC = (QualityEpidemicRouter) con.getOtherNode(getHost()).getRouter();
		
		this.localCommunity.add(peer);

		// 2) check that the connection has met the time threshold. If so:
		if(this.neighbourSet.get(peer) >= this.familiarThreshold) {
			
			
			// 1 if the node is not in the local community, give it a chance with this promotion mechanism
			// if the device is not in the local community, but is also the highest scoring node we have in the neighbour table
			// and is above the familiar threshold, then promote it.
			if(!this.localCommunity.contains(peer) && isMostConnected(peer))
			{
				//System.out.println("Peer " + peer + " secondary promoted  to LC.");
				this.localCommunity.add(peer);
			}
			
			
			// 2) Lower Natural Sets
			// Test for conditions when the local communities should be merged
				// Compute set union
				Set<DTNHost> commUnion = new HashSet<DTNHost>();
				commUnion.addAll(this.localCommunity);
				commUnion.addAll(peerC.localCommunity);  
				
				// compute intersection of the two local communities
				// (the result is the same from both node's perspective)
				int count = 0;
				for(DTNHost h : this.localCommunity)
					if(peerC.localCommunity.contains(h))
						count++;
				
				// merge communities if enough nodes are common
				if(count > this.lambda * commUnion.size())
				{
					this.localCommunity.addAll(peerC.localCommunity);
				}

			// 3) More overlap
				// if communities have no overlap
				if(count == 0)
				{
					this.localCommunity.addAll(peerC.localCommunity);			
				}

		}
		
	}
	
	protected boolean commumesWithHost(DTNHost h)
	{
		return(this.localCommunity.contains(h));
	}
	
	
	/**
	 * Tries to send all other messages to all connected hosts
	 * 
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		
		List<Tuple<Message, Connection>> messages = 
			new ArrayList<Tuple<Message, Connection>>(); 

		Collection<Message> msgCollection = getMessageCollection();

		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			QualityEpidemicRouter othRouter = (QualityEpidemicRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				// Which of us has the dest in our local communities, this host or the peer
				boolean peerInCommunity = othRouter.commumesWithHost(m.getTo());
				
				if(peerInCommunity)  {// peer is in local commun. of dest
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}			
		}

		if (messages.size() == 0) {
			return null;
		}

		// sort the message-connection tuples
		return tryMessagesForConnected(messages);	// try to send messages
	}
	
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; 
		}
		
		if (exchangeDeliverableMessages() != null) {
			return; 
		}
		
		/**
		 * 
		 * Start of Simple logic
		 * 
		 */
		// For each connection increment the connection time by 1		
		for(Connection c : getConnections()) {
			DTNHost peer = c.getOtherNode(getHost());
			QualityEpidemicRouter peerC = (QualityEpidemicRouter) c.getOtherNode(getHost()).getRouter();
			
			if(this.neighbourSet.containsKey(peer))
				this.neighbourSet.put(peer, this.neighbourSet.get(peer) + 1);
			else
				this.neighbourSet.put(peer, 1.0);
		}
		
		
		double simTime = SimClock.getTime(); // (seconds since start)
		double timeInDay = simTime % 120; // every 120 seconds or so check local community information with connected nodes
		if(timeInDay == 0) {
			for(Connection c : getConnections())
			{
				checkLocalCommunity(c);
			}
		}
		
		// try messages to destinations
		tryOtherMessages();
		
	}

	public Set<DTNHost> getLocalCommunity() {
		return this.localCommunity;
	}
	
}