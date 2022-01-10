public class Message {

    public static final int DATA_SIZE = 24;

    public StationClass stationClass;
    public byte[] data; //24 Byte
    public byte slotNumber;
    public long timeStamp;
    public long arrivalTime;

    public Message copy() {
        Message copy = new Message();
        copy.stationClass = stationClass;
        copy.data = data;
        copy.slotNumber = slotNumber;
        copy.timeStamp = timeStamp;
        copy.arrivalTime = arrivalTime;

        return copy;
    }

    /**
     * Prints out the Message
     */
    public void printer() {
        System.out.println("------------------Message------------------");
        System.out.println("-------------------------------------------");
        System.out.println("Station Class: " + (stationClass == StationClass.A ? 'A' : 'B'));
        System.out.println("Data: ");
        for (int i = 0; i < data.length; i++) {
            System.out.println((char) data[i]);
        }
        System.out.println();
        System.out.flush();

        System.out.println("Slot number: " + slotNumber);
        System.out.println("Timestamp: " + timeStamp);
        System.out.println("Arrival Time: " + arrivalTime);

        System.out.println("-------------------------------------------");
        System.out.println("---------------/Message End/---------------");
    }

    /**
     * Converts a byte array into a message
     * @param buffer
     * @param arrivalTime
     * @return
     */
    public static Message decompose(byte[] buffer, long arrivalTime) {
        Message receivedMsg = new Message();

        //byte[0]: StationClass
        if (buffer[0] == 'A') {
            receivedMsg.stationClass = StationClass.A;
        } else { //if (buffer[0] == 'B')
            receivedMsg.stationClass = StationClass.B;
        }

        //data : bytes 1-24
        byte[] data = new byte[DATA_SIZE];

        for (int i = 1; i <= 24; i++) {
            data[i - 1] = buffer[i];
        }
        receivedMsg.data = data;

        //byte 25: slotNumber
        receivedMsg.slotNumber = (byte) (buffer[25] - 1);

        //byte 26-33 : timestamp
        byte[] timeStampBytes = new byte[8];
        for (int i = 26; i <= 33; i++) {
            timeStampBytes[i - 26] = buffer[i];
        }
        receivedMsg.timeStamp = convertToLong(timeStampBytes);
        receivedMsg.arrivalTime = arrivalTime;

        return receivedMsg;
    }

    /**
     * Converts a Message into a byte array
     * @param msg
     * @return
     */
    public static byte[] compose(Message msg) {
        byte[] result = new byte[34];

        //byte[0] : StationClass
        if (msg.stationClass == StationClass.A) {
            result[0] = 'A';
        } else { //if (msg.stationClass == StationClass.B)
            result[0] = 'B';
        }

        //byte 1-25: data
        for (int i = 0; i < msg.data.length; i++) {
            result[i + 1] = msg.data[i];
        }

        //byte 25: slot number
        result[25] = (byte) (msg.slotNumber + 1);

        //byte 26-33: timestamp
        byte[] timeStamp = convertToByte(msg.timeStamp);

        for (int i = 0; i < timeStamp.length; i++) {
            result[i + 26] = timeStamp[i];
        }
        return result;
    }

    //Helper methods to convert long to Byte and vice versa
    private static byte[] convertToByte(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (1 & 0xFF);
            l >>= 8;
        }
        return result;
    }


    private static long convertToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }
}
