import java.util.ArrayList;

public class MessageList extends Accessible{
    public ArrayList<Message> _messageList;

    public MessageList copy() {
        ArrayList<Message> copyList = new ArrayList<>();
        MessageList copy;

        for (Message m : _messageList) {
            copyList.add(m.copy());
        }
        copy = new MessageList();
        copy._messageList = copyList;

        return copy;
    }
}
