// Routing writen by Huong. Routing based on logic ID of node.

package routing;

import java.util.ArrayList;
import java.util.List;

import com.sun.corba.se.impl.orbutil.closure.Constant;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import core.Tuple;

public class MyRouter extends ActiveRouter {
	public boolean checkSetLogicID = false;

	public MyRouter(Settings s) {
		super(s);
		// TODO Auto-generated constructor stub
	}

	public MyRouter(MyRouter testRouter) {
		super(testRouter);
	}

	@Override
	public MyRouter replicate() {
		return new MyRouter(this);
	}

	/**
	 * Set logic ID for node. 
	 */
	
	public void setLogicID(){
		//set logic ID cho tung node o day, cai nay hien gio dang gan bang tay
		DTNHost thisNode = this.getHost();
		if (thisNode.toString().equals("s0")){
			thisNode.setLogicID("000");
			thisNode.status = constant.FIX;
			return;
		}
		if (thisNode.toString().equals("s1")){
			thisNode.setLogicID("100");
			thisNode.status = constant.FIX;
			return;
		}
		if (thisNode.toString().equals("s2")){
			thisNode.setLogicID("200");
			thisNode.status = constant.FIX;
			return;
		}
		if (thisNode.toString().equals("s3")){
			thisNode.setLogicID("300");
			thisNode.status = constant.FIX;
			return;
		}
		if (thisNode.toString().equals("s4")){
			thisNode.setLogicID("400");
			thisNode.status = constant.FIX;
			return;
		}
		if (thisNode.toString().startsWith("a")) { // group 1
			constant.countG1 ++;
			if (constant.countG1 < 10) {
				thisNode.setLogicID("00"+constant.countG1);
			}
			else thisNode.setLogicID("0"+constant.countG1);
			System.out.println(thisNode.logicID);
			//thisNode.setHomeAgent();			
		}
		if (thisNode.toString().startsWith("b")) { // group 2
			constant.countG2 ++;
			if (constant.countG2 < 10) {
				thisNode.setLogicID("10"+constant.countG2);
			}
			else thisNode.setLogicID("1"+constant.countG2);
			System.out.println(thisNode.logicID);
			//thisNode.setHomeAgent();			
		}
		if (thisNode.toString().startsWith("c")) { // group 2
			constant.countG3 ++;
			if (constant.countG3 < 10) {
				thisNode.setLogicID("10"+constant.countG3);
			}
			else thisNode.setLogicID("1"+constant.countG3);
			System.out.println(thisNode.logicID);
			//thisNode.setHomeAgent();			
		}
		if (thisNode.toString().startsWith("d")) { // group 2
			constant.countG3 ++;
			if (constant.countG3 < 10) {
				thisNode.setLogicID("10"+constant.countG3);
			}
			else thisNode.setLogicID("1"+constant.countG3);
			System.out.println(thisNode.logicID);
			//thisNode.setHomeAgent();			
		}
	}
	/**
	 * Add my routing here If (node have direct connection with destination) {
	 * Directly send message for destination; Return; } If (number copy of m <
	 * threshold of number * copy) { // each message has limited number of copy.
	 * For single copy, threshold of number copy = 1. Mean that message m still
	 * can forward for another intermediate node. DTNHost nextNode =
	 * Find_next_node(m); If (nextNode != null) { Forward message m to nextNode;
	 * Number copy of m ++; }
	 */
	@Override
	public void update() {
		/*
		 * Checks out all sending connections to finalize the ready ones and
		 * abort those whose connection went down. Also drops messages whose TTL
		 * <= 0 (checking every one simulated minute).
		 */
		super.update();
		
		if (!checkSetLogicID){
			setLogicID();
			checkSetLogicID = true;
		}

		// cai nay phai kiem tra lai by Huong
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		List<Connection> connections = getConnections();
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return;
		}
		List<Message> messages = new ArrayList<Message>(this
				.getMessageCollection());
		this.sortByQueueMode(messages);

		// Connection upClusterConnection = findUpCluster(thisNode);
		for (Message m : messages) {
			if (m.transferableTime > 0) {
				Connection con = findNextNode(m);
				if (con != null) {
					// Forward message m to nextNode;
					int retVal = startTransfer(m, con);
					m.transferableTime--;
				}
			}
		}
	}

	/**
	 * This function written by Huong Return the score of a host for deliver
	 * Message m. In simplest, this score equals the number of prefix this host
	 * share with destination
	 * 
	 * @param h
	 * @param m
	 * @return
	 */
	public int scoreOfDeliver(DTNHost h, Message m) {
		DTNHost destination = m.getTo();
		String s1 = h.logicID;
		String s2 = destination.logicID;
		int score = 0;
		for (int i = 0; i < s2.length(); i++)
			if (s1.charAt(i) == s2.charAt(i)) {
				score++;
			} else
				break;
		return score;
	}

	/**
	 * written by Huong need to be improve Compare two host and point out what
	 * host is more suitable In simpleast: higher score has higher deliver prob?
	 * If this host is fixed and share the same number of prefix. They have
	 * higher point
	 * 
	 * @param h1
	 * @param h2
	 * @param m
	 * @return
	 */
	public DTNHost compareProbOfDeliver(DTNHost h1, DTNHost h2, Message m) {
		if (scoreOfDeliver(h1, m) > scoreOfDeliver(h2, m)) {
			return h1;
		} else {
			if (scoreOfDeliver(h1, m) < scoreOfDeliver(h2, m)) {
				return h2;
			} else {
				if (h1.status.equals(constant.FIX))
					return h1;
				if (h2.status.equals(constant.FIX))
					return h2;
			}
		}
		return h1;
	}

	public Connection findNextNode(Message m) {
		DTNHost nextNode = this.getHost();
		Connection con = null;
		DTNHost n = null;
		List<Connection> connections = getConnections();
		for (Connection c : connections) {
			n = compareProbOfDeliver(nextNode, c.getOtherNode(this
					.getHost()), m);
			if (!n.toString().equals(nextNode.toString())){ // find new node?
				con = c;
				nextNode = n;
			}
		}
		return con;
	}

}
