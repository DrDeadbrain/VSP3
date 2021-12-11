import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

/**
 * Creates a multicast socket and joins the given group
 */
public class Connect {
    private MulticastSocket _sendSocket;
    private MulticastSocket _recvSocket;
    private int _port;
    private String _address;
    private InetAddress _group;
    private String _ifName;
    private static final int FRAMELENGTH = 34;

    public Connect(int port, String address, String ifname) {
        _port = port;
        _address = address;
        _ifName = ifname;
    }

    /**
     * Setup internal socket for receiving and sending
     */
    public boolean init() {
        try {
            _sendSocket = new MulticastSocket(_port);
            _recvSocket = new MulticastSocket(_port);
            if (!_ifName.equals("none")) {
                _sendSocket.setNetworkInterface(NetworkInterface.getByName(_ifName));
                _recvSocket.setNetworkInterface(NetworkInterface.getByName(_ifName));
            }
        } catch (IOException e) {
            System.err.println("new Multicast(PORT)");
            e.printStackTrace();
            destroy();
            return false;
        }
        try {
            _group = InetAddress.getByName(_address);
            if (_group == null) {
                System.err.println("getByName");
            }
        } catch (UnknownHostException e) {
            System.err.println("InetAddress.getByName");
            e.printStackTrace();
            destroy();
            return false;
        }
        try {
            _sendSocket.joinGroup(_group);
            _recvSocket.joinGroup(_group);
        } catch (IOException e) {
            System.err.println("ms.koinGroup");
            e.printStackTrace();
            destroy();
            return false;
        }
        return true;
    }

    /**
     * Sends something to the multicast group
     */
    public void send(byte[] buffer) {
        if (buffer.length != FRAMELENGTH) {
            System.err.println("Buffer has wrong length");
            return;
        }
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, _group, _port);
        try {
            _sendSocket.send(packet);
        } catch (IOException e ) {
            System.err.println("ms.send");
            destroy();
        }
    }

    /**
     * Receives a datagram from multicast group; BLOCKING
     */
    public void receive(DatagramPacket packet) {
        if (packet.getData().length != FRAMELENGTH) {
            System.err.println("Buffer has wrong length");
            return;
        }
        try {
            _recvSocket.receive(packet);
        } catch (IOException e) {
            System.err.println("ms.receive");
            destroy();
        }
    }

    private void destroy() {
        _sendSocket.close();
        _recvSocket.close();
        _group = null;
    }
}