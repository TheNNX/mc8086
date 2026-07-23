package thennx.mcx86.item;

public abstract class CardItem extends AbstractComponentItem {
    private final boolean longCard;

    public CardItem(boolean longCard) {
        super(new Properties());

        this.longCard = longCard;
    }

    public final boolean isLongCard() {
        return longCard;
    }
}
