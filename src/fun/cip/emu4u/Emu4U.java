package fun.cip.emu4u;

import javacard.framework.*;
import javacardx.apdu.ExtendedLength;

public class Emu4U extends Applet implements ExtendedLength {

    private final File[] files;
    private final short fileCount;
//    private final boolean ephemeral;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new Emu4U(bArray, bOffset, bLength);
    }

    protected Emu4U(byte[] bArray, short bOffset, byte bLength) {
        short offset = bOffset;
        offset += (short) (1 + (bArray[offset] & 0xFF));
        offset += (short) (1 + (bArray[offset] & 0xFF));
        offset++;

//        ephemeral = bArray[offset++] != 0x00;
        byte nFiles = bArray[offset++];

        fileCount = (short) (nFiles & 0xFF);
        files = new File[fileCount];

        for (short i = 0; i < fileCount; i++) {
            if (bArray[offset] != (byte) 0x00
                    || bArray[(short) (offset + 1)] != 0x00
                    || bArray[(short) (offset + 2)] != 0x00
                    || bArray[(short) (offset + 3)] != 0x00)
                files[i] = new File(bArray[offset], bArray[(short) (offset + 1)], bArray[(short) (offset + 2)], bArray[(short) (offset + 3)]);

            offset += 4;
        }

        register();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) return;

        byte[] buf = apdu.getBuffer();

        if (buf[ISO7816.OFFSET_CLA] != Constants.CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buf[ISO7816.OFFSET_INS]) {
            case Constants.INS_AUTHENTICATE:
                handleAuthenticate(apdu);
                break;
            case Constants.INS_READ:
                handleRead(apdu);
                break;
            case Constants.INS_WRITE:
                handleWrite(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void handleAuthenticate(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte[] out = {0x0A, 0x0B, 0x0C, 0x0D};
        Util.arrayCopyNonAtomic(out, (short) 0, buf, (short) 0, (short) out.length);
        apdu.setOutgoingAndSend((short) 0, (short) out.length);
    }

    private void handleRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        apdu.setIncomingAndReceive();

        short lc = apdu.getIncomingLength();
        if (lc != 3) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        short offsetCdata = apdu.getOffsetCdata();

        File file = resolveFile(buf[ISO7816.OFFSET_P2]);

        short chunkIndex = File.getChunkIndex((byte) 0x00, buf[offsetCdata], buf[(short) (offsetCdata + 1)], buf[(short) (offsetCdata + 2)]);
        short offsetInChunk = File.getOffsetInChunk((byte) 0x00, buf[offsetCdata], buf[(short) (offsetCdata + 1)], buf[(short) (offsetCdata + 2)]);

        short le = apdu.setOutgoing();
        short available = file.getAvailable(chunkIndex, offsetInChunk);
        if (available == 0) ISOException.throwIt(ISO7816.SW_WRONG_DATA);

        short toSend = le > available ? available : le;

        apdu.setOutgoingLength(toSend);
        short blockSize = APDU.getOutBlockSize();
        while (toSend > 0) {
            available = toSend > blockSize ? blockSize : toSend;

            file.read(chunkIndex, offsetInChunk, available, buf);
            apdu.sendBytes((short) 0, available);

            toSend -= available;

            offsetInChunk += available;
            chunkIndex += (short) (offsetInChunk >> File.CHUNK_BITS);
            offsetInChunk &= File.CHUNK_MASK;
        }

    }

    private void handleWrite(APDU apdu) {
        byte[] buf = apdu.getBuffer();

        short received = apdu.setIncomingAndReceive();
        short lc = apdu.getIncomingLength();

        if (lc < 3) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

//        if (buf[ISO7816.OFFSET_P1] == 0x00 && ephemeral && JCSystem.getTransactionDepth() == 0) {
//            JCSystem.beginTransaction();
//        }

        short offsetCdata = apdu.getOffsetCdata();

        File file = resolveFile(buf[ISO7816.OFFSET_P2]);
        short payloadLen = (short) (lc - 3);

        short chunkIndex = File.getChunkIndex((byte) 0x00, buf[offsetCdata], buf[(short) (offsetCdata + 1)], buf[(short) (offsetCdata + 2)]);
        short offsetInChunk = File.getOffsetInChunk((byte) 0x00, buf[offsetCdata], buf[(short) (offsetCdata + 1)], buf[(short) (offsetCdata + 2)]);
        received -= 3;

        short available = file.getAvailable(chunkIndex, offsetInChunk);
        if (payloadLen > available) ISOException.throwIt(ISO7816.SW_WRONG_DATA);

        short written = 0;
        while (written < payloadLen) {

            file.write(chunkIndex, offsetInChunk, received, buf, (short) (offsetCdata + 3));

            written += received;

            offsetInChunk += received;
            chunkIndex += (short) (offsetInChunk >> File.CHUNK_BITS);
            offsetInChunk &= File.CHUNK_MASK;

            received = apdu.receiveBytes((short) (offsetCdata + 3));
            if (received == 0) break;
        }
    }

    private File resolveFile(byte index) {
        short i = (short) (index & 0xFF);
        if (i >= fileCount || files[i] == null) {
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
        }
        return files[i];
    }
}