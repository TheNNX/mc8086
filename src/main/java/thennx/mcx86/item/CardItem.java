package thennx.mcx86.item;

import net.minecraft.world.item.Item;
import thennx.mcx86.AbstarctDeviceFactoryItem;
import thennx.mcx86.IDeviceFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class CardItem extends Item implements IDeviceFactory {
    private final boolean longCard;

    private static final List<CardItem> CARDS = new ArrayList<>();
    private static final List<CardItem> LONG_CARDS = new ArrayList<>();

    private static CardItem[] CARDS_ARRAY = null;
    private static CardItem[] LONG_CARDS_ARRAY = null;

    public static CardItem[] getCards() {
        if (CARDS_ARRAY == null) {
            CARDS_ARRAY = CARDS.toArray(new CardItem[0]);
            CARDS.clear();
        }
        return CARDS_ARRAY;
    }

    public static Item[] getLongCards() {
        if (LONG_CARDS_ARRAY == null) {
            LONG_CARDS_ARRAY = LONG_CARDS.toArray(new CardItem[0]);
            LONG_CARDS.clear();
        }
        return LONG_CARDS_ARRAY;
    }

    public CardItem(boolean longCard) {
        super(new Properties());

        CARDS.add(this);

        this.longCard = longCard;
        if (longCard) {
            LONG_CARDS.add(this);
        }
    }

    public final boolean isLongCard() {
        return longCard;
    }
}
