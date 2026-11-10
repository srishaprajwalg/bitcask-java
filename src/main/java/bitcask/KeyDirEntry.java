package bitcask;

public class KeyDirEntry {
    private long valuePosition;
    private int valueSize; 

    public KeyDirEntry(long valuePosition, int valueSize){
        this.valuePosition = valuePosition;
        this.valueSize = valueSize;
    }

    public long getValuePosition() {
        return valuePosition;
    }

    public int getValueSize() {
        return valueSize;
    }
}
