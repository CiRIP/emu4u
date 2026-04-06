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
            int size = getInt(bArray, offset);
            offset += 4;

            if (size > 0) files[i] = new File(size);
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
        int addr = getInt3(buf, offsetCdata);

        short le = apdu.setOutgoing();
        short available = (file.length - addr) <= 0x7FFF ? (short) (file.length - addr) : 0x7FFF;
        short toSend = le > available ? available : le;

        apdu.setOutgoingLength(toSend);
        short blockSize = APDU.getOutBlockSize();
        while (toSend > 0) {
            available = toSend > blockSize ? blockSize : toSend;

            file.read(addr, available, buf);
            apdu.sendBytes((short) 0, available);

            toSend -= available;
            addr += available;
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
        int addr = getInt3(buf, offsetCdata);
        short payloadLen = (short) (lc - 3);

        if (addr < 0 || addr + payloadLen > file.length) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        JCSystem.beginTransaction();
        try {
            short firstPayloadOffset = (short) (offsetCdata + 3);
            short firstPayloadLen = (short) (received - 3);

            if (firstPayloadLen > 0) {
                file.write(buf, firstPayloadOffset, firstPayloadLen, addr);
            }

            short written = firstPayloadLen;
            while (written < payloadLen) {
                short chunk = apdu.receiveBytes(offsetCdata);
                if (chunk == 0) break;
                file.write(buf, offsetCdata, chunk, (short) (addr + written));
                written += chunk;
            }

            JCSystem.commitTransaction();
        } catch (Exception e) {
            JCSystem.abortTransaction();
            ISOException.throwIt(ISO7816.SW_UNKNOWN);
        }
    }

    private File resolveFile(byte index) {
        short i = (short) (index & 0xFF);
        if (i >= fileCount || files[i] == null) {
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
        }
        return files[i];
    }

    private static int makeInt(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | b2 << 16 & 16711680 | b3 << 8 & '\uff00' | b4 & 255;
    }

    private static int makeInt(short s1, short s2) {
        return s1 << 16 | s2 & '\uffff';
    }

    private static int getInt(byte[] bArray, short bOff) throws NullPointerException, ArrayIndexOutOfBoundsException {
        return makeInt(bArray[bOff], bArray[(short) (bOff + 1)], bArray[(short) (bOff + 2)], bArray[(short) (bOff + 3)]);
    }

    private static int getInt3(byte[] bArray, short bOff) throws NullPointerException, ArrayIndexOutOfBoundsException {
        return makeInt((byte) 0x00, bArray[bOff], bArray[(short) (bOff + 1)], bArray[(short) (bOff + 2)]);
    }
}