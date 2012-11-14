/*
 * SimpleRouter.java
 *
 * Released under GPLv3 by Matthew Orlinski (2011)
 * Simple Router logic adapted from code by PJ Dillon http://www.cs.pitt.edu/~pdillon/one/, also GPLv3
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


public class SimpleRouter extends ActiveRouter {
	
	/** Prophet router's setting namespace ({@value})*/ 
	public static final String SIMPLE_NS = "SimpleRouter";

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
	public Set<DTNHost> markedForDeletion;
	public Set<DTNHost> localCommunity;

	protected double lambda;
	protected double gamma;
	protected double familiarThreshold;

	
	public SimpleRouter(Settings s) {
		super(s);
		Settings simpleSettings = new Settings(SIMPLE_NS);
		this.lambda = simpleSettings.getDouble(LAMBDA_SETTING);
		this.gamma = simpleSettings.getDouble(GAMMA_SETTING);
		this.familiarThreshold = simpleSettings.getDouble(FAMILIAR_SETTING);

	}

	public SimpleRouter(SimpleRouter proto) {
		super(proto);
		this.lambda = proto.lambda;
		this.gamma = proto.gamma;
		this.familiarThreshold = proto.familiarThreshold;

		neighbourSet = new HashMap<DTNHost, Double>();
		localCommunity = new HashSet<DTNHost>();
		markedForDeletion = new HashSet<DTNHost>();
	}

	@Override
	public SimpleRouter replicate() {
		return new SimpleRouter(this);
	}
	
	@Override
	public void changedConnection(Connection con)
	{
	
		DTNHost myHost = getHost();
		DTNHost otherNode = con.getOtherNode(myHost);
		SimpleRouter otherRouter = (SimpleRouter)otherNode.getRouter();
		
		if(con.isUp())
		{
			if(this.neighbourSet.containsKey(otherNode)) {
				this.neighbourSet.put(otherNode, this.neighbourSet.get(otherNode) + 1);
			}
			else
				this.neighbourSet.put(otherNode, 1.0);
			
			this.localCommunity.add(myHost);
			otherRouter.localCommunity.add(otherNode);
			
			// check local community information with new connections
			checkLocalCommunity(con);

		}

	}
	
	public void checkLocalCommunity(Connection con) {
	
		DTNHost peer = con.getOtherNode(getHost());
		SimpleRouter peerC = (SimpleRouter) con.getOtherNode(getHost()).getRouter();
		

		// 2) check that the connection has met the time threshold. If so:
		if(this.neighbourSet.get(peer) >= this.familiarThreshold) {
			
			//System.out.println("Peer " + peer + " passed familiar threshold.");
			// 3) if device is in Do then remove it
			if(this.markedForDeletion.contains(peer))
				this.markedForDeletion.remove(peer);

			// 4) possibly add to the local community for future transactions
			if(!this.localCommunity.contains(peer))
			{
				/*
				 * The algorithm calls for computing the size of  the intersection of 
				 * peer's neighbourSet and this host's localCommunity. We divide that by
				 * the size of the peer's familiar set
				 */
				// compute set intersection
				
				int count=0, peerFsize = peerC.neighbourSet.size();
				
				Iterator<Entry<DTNHost, Double>> it = peerC.neighbourSet.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry pairs = it.next();
					if(this.localCommunity.contains(pairs.getKey()))
						count++;			
				}

				//if(count > 1)
				//	System.out.println("Count" + count);
				// add peer to local community if enough nodes in common
				if(((double)count)/peerFsize > this.lambda)
				{
					this.localCommunity.add(peer);
				}
			}
			// 5) Test for conditions when the local communities should be merged
			if(this.localCommunity.contains(peer))
			{
				// Compute set union
				Set<DTNHost> commUnion = new HashSet<DTNHost>(this.localCommunity.size() +
						peerC.localCommunity.size() + 2);
				commUnion.addAll(this.localCommunity);
				commUnion.addAll(peerC.localCommunity);
				
				// compute intersection of the two local communities
				// (the result is the same from both node's perspective)
				int count = 0;
				for(DTNHost h : this.localCommunity)
					if(peerC.localCommunity.contains(h))
						count++;
				
				// merge communities if enough nodes are common
				if(count > this.gamma * commUnion.size())
				{
					this.localCommunity.addAll(peerC.localCommunity);					
				}
			}	

		}
		
	}
	
	protected boolean commumesWithHost(DTNHost h)
	{
		return(this.localCommunity.contains(h));
	}
	
	/*
	protected boolean commumesWithHostIndirectly(DTNHost h)
	{
		return(this.neighbourSetCache.contains(h));
	}
	*/
	
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
			SimpleRouter othRouter = (SimpleRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				// Which of us has the dest in our local communities, this host or the peer
				boolean peerInCommunity = othRouter.commumesWithHost(m.getTo());
				boolean meInCommunity = this.commumesWithHost(m.getTo());
				boolean deliver = false;
				
				if(peerInCommunity && !meInCommunity) // peer is in local commun. of dest
					deliver = true;
				
				if (deliver) {
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
			SimpleRouter peerC = (SimpleRouter) c.getOtherNode(getHost()).getRouter();
			
			if(this.neighbourSet.containsKey(peer)) {
				this.neighbourSet.put(peer, this.neighbourSet.get(peer) + 1);
			}
			else
				this.neighbourSet.put(peer, 1.0);
		}
		
		
		double simTime = SimClock.getTime(); // (seconds since start)
		double timeInDay = simTime % 120; // ever 120 seconds check local community information with connected nodes
		if(timeInDay == 0) {
			for(Connection c : getConnections())
			{
				checkLocalCommunity(c);
			}
		}
		
		// try messages to destinations
		tryOtherMessages();
		
	}
	

	
}