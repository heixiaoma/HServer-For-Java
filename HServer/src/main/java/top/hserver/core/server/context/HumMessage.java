package top.hserver.core.server.context;

public class HumMessage {

    private String type="USER";

    private Object data;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public HumMessage() {
    }

    public HumMessage(Object data) {
        this.data = data;
    }


    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "HumMessage{" +
                "humMessageType=" + type +
                ", data=" + data +
                '}';
    }
}
