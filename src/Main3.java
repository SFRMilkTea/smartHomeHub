import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;


public class Main3 {
    private static String URL;
    private static Integer ADDRESS;
    private static final String HUBNAME = "PetCheetah";

    public static void main(String[] args) throws Exception {
        URL = args[0];
        ADDRESS = Integer.parseInt(args[1], 16);
        // начало работы - посылаем WHOISHERE
        String whoIsHere = whoIsHere();
        send(whoIsHere);
    }

    static byte[] encodeToULEB128(ArrayList<Long> array) {
        int index = 0;
        byte[] encodedBytes = new byte[20];
        for (long number : array) {
            do {
                byte b = (byte) (number & 0x7F);
                number >>= 7;
                if (number != 0) {
                    b |= 0x80; // Установка старшего бита для указания продолжения числа
                }
                encodedBytes[index] = b;
                index++;
            } while (number != 0);
        }
        return Arrays.copyOf(encodedBytes, index);
    }

    public static ArrayList<Long> decodeFromULEB128(byte[] decodedBytes) {
        ArrayList<Long> resultArray = new ArrayList();
        long result = 0;
        int shift = 0;
        int index = 1;
        while (index <= decodedBytes[0]) {
            while (true) {
                byte b = decodedBytes[index];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    resultArray.add(result);
                    break;
                }
                shift += 7;
                index++;
            }
            shift = 0;
            result = 0;
            index++;
        }
        return resultArray;
    }

    public static byte Compute_CRC8_Simple(byte[] bytes) {
        byte generator = 0x1D;
        byte crc = 0;
        for (byte currByte : bytes) {
            crc ^= currByte;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x80) != 0) {
                    crc = (byte) ((crc << 1) ^ generator);
                } else {
                    crc <<= 1;
                }
            }
        }
        return crc;
    }

    public static void divideToPackets(byte[] decodedBytes) throws
            URISyntaxException, IOException, InterruptedException {
        byte[] packetWithoutCRC8 = new byte[decodedBytes[0]];
        System.arraycopy(decodedBytes, 1, packetWithoutCRC8, 0, packetWithoutCRC8.length);
        byte[] packet = new byte[decodedBytes[0] + 2];
        byte[] remainedPacket = new byte[decodedBytes.length - (decodedBytes[0] + 2)];
        // остаток
        System.arraycopy(decodedBytes, packet.length, remainedPacket, 0, remainedPacket.length);
        // пакет
        System.arraycopy(decodedBytes, 0, packet, 0, packet.length);
        ArrayList<Long> decodedPacket = decodeFromULEB128(packet);
        // проверка контрольной суммы
        if (packet[packet.length - 1] == Compute_CRC8_Simple(packetWithoutCRC8)) {
            controller(decodedPacket);
        }
        if (remainedPacket.length > 0) {
            divideToPackets(remainedPacket);
        }
    }

    // определяем что за команда к нам пришла и что дальше делать
    public static void controller(ArrayList<Long> packet) throws
            IOException, InterruptedException, URISyntaxException {
        if (packet.get(4) == 1L) {
            String iAmHere = iAmHere();
            send(iAmHere);
        } else if (packet.get(4) == 2L) {
            String getStatus = getStatus(packet.get(0), packet.get(3));
            send(getStatus);
        } else if (packet.get(4) == 4L && (packet.get(3) == 3 || packet.get(3) == 4 || packet.get(3) == 5)) {
            String setStatus = setStatus(packet.get(0), packet.get(3), packet.get(5));
            send(setStatus);
        }

    }

    public static String whoIsHere() {
        ArrayList<Long> array = new ArrayList();
        long src = ADDRESS; // адрес отправителя
        long dst = 16383; // адрес получателя
        long serial = 1; // номер пакета
        long devType = 1;  // тип устройства
        long cmd = 1; // команда протокола

        array.add(src);
        array.add(dst);
        array.add(serial);
        array.add(devType);
        array.add(cmd);

        return encode(array, HUBNAME);
    }

    public static String iAmHere() {
        ArrayList<Long> array = new ArrayList();
        long src = ADDRESS; // адрес отправителя
        long dst = 16383; // адрес получателя
        long serial = 1; // номер пакета
        long devType = 1;  // тип устройства
        long cmd = 2; // команда протокола

        array.add(src);
        array.add(dst);
        array.add(serial);
        array.add(devType);
        array.add(cmd);

        return encode(array, HUBNAME);
    }

    public static String getStatus(long deviceAddress, long deviceType) {
        ArrayList<Long> array = new ArrayList();
        long src = ADDRESS; // адрес отправителя
        long serial = 1; // номер пакета
        long cmd = 3; // команда протокола

        array.add(src);
        array.add(deviceAddress);
        array.add(serial);
        array.add(deviceType);
        array.add(cmd);

        return encode(array, null);
    }

    public static String setStatus(long deviceAddress, long deviceType, long previousStatus) {
        ArrayList<Long> array = new ArrayList();
        long src = ADDRESS; // адрес отправителя
        long serial = 1; // номер пакета
        long cmd = 5; // команда протокола
        long status;
        if (previousStatus == 1L) {
            status = 0L;
        } else {
            status = 1L;
        }
        array.add(src);
        array.add(deviceAddress);
        array.add(serial);
        array.add(deviceType);
        array.add(cmd);
        array.add(status);

        return encode(array, null);
    }

    public static String encode(ArrayList<Long> array, String name) {
        byte[] encodedBytes = encodeToULEB128(array);
        byte[] resBytes;
        if (name != null) {
            // переводим имя
            byte[] myNameBytes = name.getBytes();
            byte[] myNameBytesWithLen = new byte[myNameBytes.length + 1];

            myNameBytesWithLen[0] = (byte) myNameBytes.length;
            System.arraycopy(myNameBytes, 0, myNameBytesWithLen, 1, myNameBytes.length);
            byte[] combArray = new byte[encodedBytes.length + myNameBytesWithLen.length];

            System.arraycopy(encodedBytes, 0, combArray, 0, encodedBytes.length);
            System.arraycopy(myNameBytesWithLen, 0, combArray, encodedBytes.length, myNameBytesWithLen.length);
            resBytes = combArray;
        } else {
            resBytes = encodedBytes;
        }
        byte crc8 = Compute_CRC8_Simple(resBytes);
        byte[] resArray = new byte[resBytes.length + 2];
        resArray[resArray.length - 1] = crc8;
        int len = resBytes.length;
        resArray[0] = (byte) len;
        System.arraycopy(resBytes, 0, resArray, 1, resBytes.length);
        byte[] encodedBase64Bytes = Base64.getUrlEncoder().withoutPadding().encode(resArray);
        return new String(encodedBase64Bytes);
    }

    public static void decode(String data) throws URISyntaxException, IOException, InterruptedException {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(data);
        divideToPackets(decodedBytes);
    }

    public static void send(String message) throws IOException, InterruptedException, URISyntaxException {
        HttpClient httpClient = HttpClient.newHttpClient();
        String url = URL;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(message))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // Обработка ответа
        int statusCode = response.statusCode();
        String responseBody = response.body();
        if (statusCode == 200) {
            decode(responseBody);
        } else if (statusCode == 204) {
            System.exit(0);
        } else {
            System.exit(99);
        }
    }
}
