package fun.cip.emu4u;

public interface Constants {
    byte CLA = (byte) 0x90;

    byte INS_AUTHENTICATE = 0x1B;
    byte INS_READ = (byte) 0xAD;
    byte INS_WRITE = (byte) 0x8D;
}
