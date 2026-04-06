package fun.cip.emu4u;

import javacard.framework.Util;

/**
 * This exists because we need to store more than 0x7FFF bytes of data, and Javacard
 * doesn't support single arrays larger than that, so we simulate one with an array of arrays.
 */
public class File {

    private static final short CHUNK_BITS = 14;
    private static final short CHUNK_SIZE = (short) (1 << CHUNK_BITS);
    private static final int OFFSET_MASK = CHUNK_SIZE - 1;
    private static final short CHUNK_MASK = ~OFFSET_MASK;

    private final Object[] chunks;
    public final int length;

    public File(int length) {
        this.length = length;

        short numChunks = (short) ((length - 1 + CHUNK_SIZE) >> CHUNK_BITS);
        chunks = new Object[numChunks];

        int remaining = length;
        for (short i = 0; i < numChunks; i++) {
            short chunkLen = remaining >= CHUNK_SIZE ? CHUNK_SIZE : (short) remaining;
            chunks[i] = new byte[chunkLen];
            remaining -= CHUNK_SIZE;
        }
    }

    /**
     * Read length bytes from address into dest.
     * dest must be at least length bytes long.
     */
    public void read(int address, short length, byte[] dest) {
        short remaining = length;
        short destOffset = (short) 0;

        while (remaining > (short) 0) {
            short offsetInChunk = (short) (address & OFFSET_MASK);
            byte[] chunk = (byte[]) chunks[(short) ((address & CHUNK_MASK) >> CHUNK_BITS)];

            short available = (short) (CHUNK_SIZE - offsetInChunk);
            short toCopy = remaining < available ? remaining : available;

            Util.arrayCopyNonAtomic(chunk, offsetInChunk, dest, destOffset, toCopy);

            address += toCopy;
            remaining -= toCopy;
            destOffset += toCopy;
        }
    }

    /**
     * Write length bytes from src starting at srcOffset into the file at address.
     */
    public void write(byte[] src, short srcOffset, short length, int address) {
        short remaining = length;
        short srcPos = srcOffset;

        while (remaining > (short) 0) {
            short offsetInChunk = (short) (address & OFFSET_MASK);
            byte[] chunk = (byte[]) chunks[(short) ((address & CHUNK_MASK) >> CHUNK_BITS)];

            short available = (short) (CHUNK_SIZE - offsetInChunk);
            short toCopy = remaining < available ? remaining : available;

            Util.arrayCopyNonAtomic(src, srcPos, chunk, offsetInChunk, toCopy);

            address += toCopy;
            remaining -= toCopy;
            srcPos += toCopy;
        }
    }
}