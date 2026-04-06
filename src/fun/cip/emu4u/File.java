package fun.cip.emu4u;

import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * This exists because we need to store more than 0x7FFF bytes of data, and Javacard
 * doesn't support single arrays larger than that, so we simulate one with an array of arrays.
 */
public class File {

    public static final short CHUNK_BITS = 14;
    public static final short CHUNK_SIZE = (short) (1 << CHUNK_BITS);
    public static final short CHUNK_MASK = (short) (CHUNK_SIZE - 1);

    private final Object[] chunks;

    private final short fullChunks;
    private final short lastChunkLen;

    public File(byte l3, byte l2, byte l1, byte l0) {

        if ((l3 & 0xE0) != 0) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        fullChunks = getChunkIndex(l3, l2, l1, l0);
        lastChunkLen = getOffsetInChunk(l3, l2, l1, l0);
        chunks = new Object[(short) (fullChunks + (lastChunkLen > 0 ? (short) 1 : (short) 0))];

        for (short i = 0; i < fullChunks; i++)
            chunks[i] = new byte[CHUNK_SIZE];

        if (lastChunkLen > 0)
            chunks[fullChunks] = new byte[lastChunkLen];
    }

    /**
     * Read length bytes from address into dest.
     * dest must be at least length bytes long.
     */
    public short read(byte a3, byte a2, byte a1, byte a0, short length, byte[] dest) {
        if ((a3 & 0xE0) != 0) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        if (length > dest.length) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        return read((short) ((a3 << 10) | (a2 << 2) | (a1 >> 6)), (short) (((a1 & 0x3F) << 8) | a0), length, dest);
    }

    public short read(short chunkIndex, short offsetInChunk, short length, byte[] dest) {
        if (length > dest.length) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        short destOffset = (short) 0;

        while (length > (short) 0) {

            if (chunkIndex > fullChunks) break;
            if (chunkIndex == fullChunks && offsetInChunk >= lastChunkLen) break;

            byte[] chunk = (byte[]) chunks[chunkIndex];

            final short available = (short) ((chunkIndex == fullChunks ? lastChunkLen : CHUNK_SIZE) - offsetInChunk);
            final short toCopy = length < available ? length : available;

            Util.arrayCopyNonAtomic(chunk, offsetInChunk, dest, destOffset, toCopy);

            length -= toCopy;
            destOffset += toCopy;
            chunkIndex++;
            offsetInChunk = (short) 0;
        }

        return destOffset;
    }

    /**
     * Write length bytes from src starting at srcOffset into the file at address.
     */
    public short write(byte a3, byte a2, byte a1, byte a0, short length, byte[] src, short srcOffset) {
        if ((a3 & 0xE0) != 0) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        return write((short) ((a3 << 10) | (a2 << 2) | (a1 >> 6)), (short) (((a1 & 0x3F) << 8) | a0), length, src, srcOffset);
    }

    public short write(short chunkIndex, short offsetInChunk, short length, byte[] src, short srcOffset) {
        if ((short) (srcOffset + length) > src.length) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        while (length > (short) 0) {

            if (chunkIndex > fullChunks) break;
            if (chunkIndex == fullChunks && offsetInChunk >= lastChunkLen) break;

            byte[] chunk = (byte[]) chunks[chunkIndex];

            final short available = (short) ((chunkIndex == fullChunks ? lastChunkLen : CHUNK_SIZE) - offsetInChunk);
            final short toCopy = length < available ? length : available;

            Util.arrayCopyNonAtomic(src, srcOffset, chunk, offsetInChunk, toCopy);

            length -= toCopy;
            srcOffset += toCopy;
        }

        return srcOffset;
    }

    /**
     * Return the number of bytes available in the file at address.
     */
    public short getAvailable(byte a3, byte a2, byte a1, byte a0) {
        if ((a3 & 0xE0) != 0) ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        return getAvailable((short) ((a3 << 10) | (a2 << 2) | (a1 >> 6)), (short) (((a1 & 0x3F) << 8) | a0));
    }

    public short getAvailable(short chunkIndex, short offsetInChunk) {

        if (chunkIndex > fullChunks) return 0;
        if (chunkIndex == fullChunks && offsetInChunk >= lastChunkLen) return 0;
        if (chunkIndex == fullChunks) return (short) (lastChunkLen - offsetInChunk);
        if ((short) (fullChunks - chunkIndex) > 2) return 0x7FFF;
        if ((short) (fullChunks - chunkIndex) == 2 && (short) (offsetInChunk - 1) <= lastChunkLen) return 0x7FFF;
        if ((short) (fullChunks - chunkIndex) == 2) return (short) (0x7FFF - offsetInChunk + 1 + lastChunkLen);

        return (short) (CHUNK_SIZE - offsetInChunk + lastChunkLen);
    }

    public static short getChunkIndex(byte a3, byte a2, byte a1, byte a0) {
        return (short) ((a3 << 10 & 0x7C00) | (a2 << 2 & 0x3FC) | (a1 >> 6 & 0x03));
    }

    public static short getOffsetInChunk(byte a3, byte a2, byte a1, byte a0) {
        return (short) (((a1 & 0x3F) << 8 & 0x3F00) | a0 & 0xFF);
    }
}