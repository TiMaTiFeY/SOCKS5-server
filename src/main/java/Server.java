import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;

/**
 * @author Timofey Pletnev (https://github.com/TiMaTiFeY)
 *
 */
public class Server {
    public static void main(String[] args) {
        try (final ServerSocketChannel open = ServerSocketChannel.open();
             final Selector selector = Selector.open()) {
            open.bind(new InetSocketAddress(2022));
            open.configureBlocking(false);
            open.register(selector, SelectionKey.OP_ACCEPT);
            final HashMap<SocketChannel, ByteBuffer> connections = new HashMap<>();
            while (true) {
                selector.select(); // blocking
                for (SelectionKey selectedKey : selector.selectedKeys()) {
                    if (selectedKey.isAcceptable()) {
                        final SocketChannel channel = open.accept();
                        connections.put(channel, ByteBuffer.allocateDirect(128));
                        channel.configureBlocking(false);
                        channel.register(selector, SelectionKey.OP_READ);
                        continue;
                    }
                    if (selectedKey.isReadable()) {
                        final SocketChannel channel = (SocketChannel) selectedKey.channel();
                        ByteBuffer byteBuffer = connections.get(channel);
                        int read = channel.read(byteBuffer);
                        if (read == -1) {
                            closeChannel(channel);
                            continue;
                        }
                        if (read == 2) {
                            byteBuffer.flip();
                            selectedKey.interestOps(SelectionKey.OP_WRITE);
                        }
                        continue;
                    }
                    if (selectedKey.isWritable()) {
                        final SocketChannel channel = (SocketChannel) selectedKey.channel();
                        ByteBuffer byteBuffer = connections.get(channel);
                        for (SocketChannel otherChannel : connections.keySet()) {
                            if (!otherChannel.equals(channel)) {
                                ByteBuffer cloned = byteBuffer.duplicate();
                                while (cloned.hasRemaining()) {
                                    otherChannel.write(cloned);
                                }
                            }
                        }
                        byteBuffer.compact();
                        selectedKey.interestOps(SelectionKey.OP_READ);
                    }
                }
                selector.selectedKeys().clear();
                connections.keySet().removeIf(sc -> !sc.isOpen());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void closeChannel(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}
