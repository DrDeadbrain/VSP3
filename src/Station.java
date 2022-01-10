import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.util.ArrayList;

public class Station {

    private static final int SLOT_LENGTH = 40;
    private static final int HALF_SLOT_LENGTH = 20;
    private static final int QUARTER_SLOT_LENGTH = 10;

    private static StationClass _stationClass;
    private static Connect _connection;
    private static byte _currentSlot;
    private static long _utcOffset;
    private static long _syncOffset = 0;
    private static char[] _data = new char[Message.DATA_SIZE];
    private static MessageList _receivedMessagesThisFrame;
    private static MessageList _receivedMessagesLastFrame;
    private static final ArrayList<Byte> _slotsMarkedAsUsedForNextFrame = new ArrayList<>();

    private static class Reader extends Thread implements Runnable {
        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                try {
                    char[] data = new char[Message.DATA_SIZE];
                    while (reader.read(data) != Message.DATA_SIZE)
                        ;
                    synchronized (_data) {
                        _data = data;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class Receiver extends Thread implements Runnable {
        @Override
        public void run() {
            waitForInitialFrame("Receiver");

            while (true) {
                byte[] buffer = new byte[34];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                _connection.receive(packet);
                long arrivalTime = System.currentTimeMillis() + _utcOffset + _syncOffset;

                Message recvMsg = new Message();
                recvMsg = Message.decompose(buffer, arrivalTime);
                //recvMsg.printer();
                accessMessageList(AccessMode.ADD, recvMsg);

                synchronized (_slotsMarkedAsUsedForNextFrame) {
                    _slotsMarkedAsUsedForNextFrame.add(recvMsg.slotNumber);
                }
            }
        }
    }

    private static class FrameEvaluator extends Thread implements Runnable {
        @Override
        public void run() {
            //wait for start of next frame
            waitForInitialFrame("Eval");

            long handledFrameNo = 0;
            while (true) {
                //at end of each frame evaluate
                long curTime = System.currentTimeMillis() + _utcOffset + _syncOffset;
                long thisFrameNo = (long) (((double) (curTime)) / 1000.0);
                if (curTime % 1000 <= QUARTER_SLOT_LENGTH && handledFrameNo != thisFrameNo) {
                    if ((handledFrameNo != 0) && (thisFrameNo != handledFrameNo + 1)) {
                        System.err.println("[Eval] MISSED SOME FRAMES");
                    }
                    handledFrameNo = thisFrameNo;

                    //handle the last frame that occured (thisFrame)
                    //thisFrame is the frame we are currently evaluating
                    //lastFrame is the last frame we already handled
                    MessageList thisFrame = (MessageList) accessMessageList(AccessMode.GET, null);
                    MessageList lastFrame = _receivedMessagesLastFrame;

                    int diffSum = 0;
                    int numOfClassAMsgs = 0;
                    //evaluate in which slots other stations have sent something
                    assert thisFrame != null;
                    for (Message m : thisFrame._messageList) {

                        //difference Timestamp - timeOfArrival for this message
                        if (m.stationClass == StationClass.A) {
                            numOfClassAMsgs++;
                            long diff = m.timeStamp - m.arrivalTime;
                            if (diff > 1000) {
                                System.err.println("Difference between stamp and arrival was too large; " +
                                        "probably not in frame");
                            } else {
                                diffSum += diff;
                            }
                        }
                    }
                    long incOffset;
                    if (numOfClassAMsgs != 0) {
                        incOffset = diffSum / numOfClassAMsgs;
                    } else {
                        incOffset = 0;
                    }
                    _syncOffset += incOffset;
                    System.out.println("New sync offset set to + " + _syncOffset);

                    _receivedMessagesLastFrame = thisFrame;
                    accessMessageList(AccessMode.CLEAR, null);

                    synchronized (_slotsMarkedAsUsedForNextFrame) {
                        _slotsMarkedAsUsedForNextFrame.clear();
                    }
                    try {
                        Thread.sleep(999);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static class Sender extends Thread implements Runnable {
        @Override
        public void run() {
            waitForInitialFrame("Sender");
            waitForInitialFrame("Sender");

            long handledFrameNo = 0;
            while (true) {
                long curTime = System.currentTimeMillis() + _utcOffset + _syncOffset;
                long thisFrameNo = (long) (((double)(curTime)) / 1000.0);

                int slotWindowStart = ((_currentSlot * SLOT_LENGTH) + (HALF_SLOT_LENGTH));
                int slotWindowEnd = slotWindowStart + QUARTER_SLOT_LENGTH;

                if (((curTime % 1000 >= slotWindowStart) && (curTime % 1000 <= slotWindowEnd))
                                    && (handledFrameNo != thisFrameNo)) {
                    if ((handledFrameNo != 0) && (thisFrameNo != handledFrameNo + 1)) {
                        System.err.println("[Sender] MISSED SOME FRAMES!!");
                    }
                    handledFrameNo = thisFrameNo;

                    //read data from buffer to send
                    byte[] data;
                    synchronized (_data) {
                        data = new byte[Message.DATA_SIZE];
                        for (int b = 0; b < Message.DATA_SIZE; b++) {
                            data[b] = (byte) _data[b];
                        }
                    }
                    System.out.println("Waited for slot + " + _currentSlot + " frame offset was " + (curTime % 1000));

                    //for the next frame, pick a slot, that hasn't been marked yet in this frame
                    ArrayList<Byte> freeSlotsInTheNextFrame = new ArrayList<>();
                    for (byte i = 0; i < 24; i++) {
                        freeSlotsInTheNextFrame.add(i);
                    }

                    synchronized (_slotsMarkedAsUsedForNextFrame) {
                        for (Byte b : _slotsMarkedAsUsedForNextFrame) {
                            freeSlotsInTheNextFrame.remove(b);
                        }
                    }


                    //for testing if this works correctly
                    for (Byte b : freeSlotsInTheNextFrame) {
                        System.out.println(b + ", ");
                    }
                    System.out.println(" are free!");

                    int randomIndex = (int) (Math.random() * (double) (freeSlotsInTheNextFrame.size()));
                    System.out.println("I AM IN SLOT " + _currentSlot + "RIGHT NOW!");
                    int slotRightNow = _currentSlot;


                    if (freeSlotsInTheNextFrame.size() > 0) {
                        _currentSlot = freeSlotsInTheNextFrame.get(randomIndex);
                        int slotInNextFrame = _currentSlot;

                        //0-24
                        int slotsTillFrameEnd = 24 - slotRightNow;
                        int slotLengthsToWait = slotsTillFrameEnd + slotInNextFrame + 1;
                        int diffInMS = slotLengthsToWait * SLOT_LENGTH - 1;

                        //for testing
                        System.out.println("I WILL BE IN SLOT " + _currentSlot + " IN NEXT FRAME");
                        System.out.println("Picked slot " + _currentSlot);

                        System.out.println("I AM GONNA WAIT FOR " + diffInMS + " ms!");
                        System.out.println("I AM IN SLOT " +slotRightNow + " NEXT FRAME I AM GOING TO SEND IN SLOT " + slotInNextFrame +
                                            " SO I AM GOING TO WAIT " + slotLengthsToWait + " SLOT LENGTHS, AKA " + diffInMS + " MILLISECONDS");

                        Message newMsg = new Message();
                        newMsg.stationClass = _stationClass;
                        newMsg.data = data;
                        newMsg.slotNumber = _currentSlot;
                        newMsg.timeStamp = curTime; //Maybe get new time

                        byte[] byteMsg = Message.compose(newMsg);

                        _connection.send(byteMsg);
                        //FOR TESTING
                        long carTime = System.currentTimeMillis();
                        System.out.println("IT TOOK " + (carTime - curTime) + " MS");

                        try {
                            Thread.sleep(diffInMS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.err.println("NO SLOTS WERE FREE!!!");
                    }
                }
            }
        }
    }

    private synchronized static Accessible accessMessageList(AccessMode am, Message msg) {
        if (am == AccessMode.ADD) {
            _receivedMessagesThisFrame._messageList.add(msg);
            return null;
        } else if (am == AccessMode.GET) {
            return _receivedMessagesThisFrame.copy();
        } else { //if (am == AccessMode.CLEAR)
            _receivedMessagesThisFrame._messageList.clear();
            return null;
        }
    }

    public static void waitForInitialFrame(String name){
        while (true) {
            long currentTime = System.currentTimeMillis() + _utcOffset + _syncOffset;
            if (currentTime % 1000 == 0) {
                System.out.println("[" + name + "] Waited for initial frame");
                System.out.println("Frame of entry was " + currentTime / 1000);
                accessMessageList(AccessMode.CLEAR, null);
                break;
            }
        }
    }

//		interfaceName=$1
//		mcastAddress=$2
//		receivePort=$3
//		firstIndex=$4
//		lastIndex=$5
//		stationClass=$6
//		UTCoffsetMs=$7
    public static void main(String[] args) {
        if (args.length < 7) {
            System.err.println("Not enough arguments");
            return;
        }
        String ifname = args[0];
        String multiCastAddr = args[1];
        int port = Integer.parseInt(args[2]);
        StationClass sclass;
        if(args[5].equals("A")) {
            sclass = StationClass.A;
        } else {
            sclass = StationClass.B;
        }
        int utcOffset = Integer.parseInt(args[6]);

        _stationClass = sclass;
        _connection = new Connect(port, multiCastAddr, ifname);
        _utcOffset = utcOffset;
        if (!(_connection.init())) {
            System.err.println("Couldn't setup connection");
            return;
        }

        _receivedMessagesThisFrame = new MessageList();
        _receivedMessagesLastFrame = new MessageList();

        _receivedMessagesThisFrame._messageList = new ArrayList<>();
        _receivedMessagesLastFrame._messageList = new ArrayList<>();

        FrameEvaluator _evaluator = new FrameEvaluator();
        Receiver _receiver = new Receiver();
        Sender _sender = new Sender();
        Reader _dataReader = new Reader();

        _evaluator.start();
        _sender.start();
        _receiver.start();
        _dataReader.start();
    }
}