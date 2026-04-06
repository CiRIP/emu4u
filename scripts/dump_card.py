import logging
import time
from pathlib import Path

from smartcard.CardConnection import CardConnection
from smartcard.System import readers


logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(message)s"
)


def transmit(connection: CardConnection, apdu: bytes) -> tuple[bytes, int]:
    raw_resp, sw1, sw2 = connection.transmit([byte for byte in apdu])
    sw = (sw1 << 8) | sw2
    resp = bytes(raw_resp)

    logging.info(f"APDU: {apdu.hex()} -> {resp.hex()} [{sw:04X}]")

    return resp, sw


def auth(connection: CardConnection):
    return transmit(
        connection,
        bytes.fromhex("901B000008AE519FB58A7871F404")
    )


def read(connection: CardConnection, address: int, length: int, p1: int = 0x00, p2: int = 0x02):
    return transmit(
        connection,
        bytes.fromhex(f"90AD{p1:02X}{p2:02X}03{address:06X}{length:02X}")
    )

def write(connection: CardConnection, address: int, data: bytes, p1: int = 0x00, p2: int = 0x02):
    return transmit(
        connection,
        bytes.fromhex(f"908D{p1:02X}{p2:02X}{3 + len(data):02X}{address:06X}{data.hex()}00")
    )


def main():
    available = readers()
    if not available:
        raise RuntimeError("No PC/SC readers found")

    logging.info(f"Using reader: {available[0]}")
    connection = available[0].createConnection()
    connection.connect()

    auth(connection)

    # write(connection, 0x000000, b"1234567890")
    # read(connection, 0x000000, 10)

    # for ins in range(0x00, 0x100):
    #     resp, sw = transmit(connection, bytes.fromhex(f"90{ins:02X}BABA"))
    #     if sw != 0x6D00:
    #         logging.warning(f"INS %02X exists: %04X", ins, sw)
    t0 = time.time()

    # for p2 in range(2, 3):
    #     sw = 0x9000
    #     address = 0x000000
    #     data = bytearray()
    #     step = 167
    #     while sw == 0x9000:
    #         resp, sw = read(connection, address, step, 0x00, p2)
    #         address += step
    #         data.extend(resp)
    #
    #     if data:
    #         Path(f"dump_modded_00{p2:02X}.bin").write_bytes(data)

    for p2 in range(1, 4):
        data = Path(f"dump_00{p2:02X}.bin").read_bytes()

        CHUNK_SIZE = 0x80

        for i in range(0, len(data), CHUNK_SIZE):
            resp, sw = write(connection, address=i, data=data[i:i+CHUNK_SIZE], p2=p2)

            if sw != 0x9000:
                logging.error(f"Write failed: {sw:04X}")
                break

    # data = Path("custom.bin").read_bytes()
    #
    # CHUNK_SIZE = 0x80
    #
    # for i in range(0, len(data), CHUNK_SIZE):
    #     resp, sw = write(connection, address=i, data=data[i:i+CHUNK_SIZE], p2=0x03)
    #
    #     if sw != 0x9000:
    #         logging.error(f"Write failed: {sw:04X}")
    #         break

    # data = Path("dump_0002.bin").read_bytes()
    # CHUNK_SIZE = 0x80
    # for i in range(0, 0x40, CHUNK_SIZE):
    #     resp, sw = write(connection, address=i, data=data[i:i+CHUNK_SIZE], p2=0x02)
    #
    #     if sw != 0x9000:
    #         logging.error(f"Write failed: {sw:04X}")
    #         break
    #
    # t1 = time.time()
    #
    # total = t1-t0
    #
    # logging.info(f"Total time: {total:.2f}s")

    # write(connection, 0x000000, Path("dump_0003.bin").read_bytes()[0:10], p2=0x03)
    # read(connection, 0x000000, 20, p2=0x03)


if __name__ == "__main__":
    main()
