package letrain.vehicle;

public interface Linkable {
    enum LinkSide {
        FRONT(0), BACK(1);

        final int value;

        public int getValue() {
            return this.value;
        }

        LinkSide(int value) {
            this.value = value;
        }
    }

    void link(LinkSide side, Linkable other);

    Linkable unlink(LinkSide side);

    Linkable getLinked(LinkSide side);

}
