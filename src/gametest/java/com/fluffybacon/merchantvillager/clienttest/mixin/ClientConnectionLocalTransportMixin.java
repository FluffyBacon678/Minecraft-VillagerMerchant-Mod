package com.fluffybacon.merchantvillager.clienttest.mixin;

import com.fluffybacon.merchantvillager.clienttest.ClientTestLocalTransport;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkingBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the full dedicated-server login and packet flow while replacing only
 * the hosted runner's unreliable loopback TCP channel with Minecraft's
 * official in-memory Netty transport. Vanilla uses the same local transport
 * when a client joins its integrated server.
 */
@Mixin(ClientConnection.class)
abstract class ClientConnectionLocalTransportMixin {
    @Inject(
        method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private static void merchantVillager$useLocalTestTransport(
        InetSocketAddress requestedAddress,
        NetworkingBackend ignoredBackend,
        ClientConnection connection,
        CallbackInfoReturnable<ChannelFuture> callback
    ) {
        SocketAddress localAddress = ClientTestLocalTransport.take();
        if (localAddress == null) {
            return;
        }
        if (requestedAddress.isUnresolved()
            || !requestedAddress.getAddress().isLoopbackAddress()) {
            throw new AssertionError(
                "Refusing to redirect a non-loopback client connection: " + requestedAddress
            );
        }

        NetworkingBackend localBackend = NetworkingBackend.local();
        ChannelFuture connectionFuture = new Bootstrap()
            .group(localBackend.getEventLoopGroup())
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {
                    ClientConnection.addLocalValidator(
                        channel.pipeline(),
                        NetworkSide.CLIENTBOUND
                    );
                    connection.addFlowControlHandler(channel.pipeline());
                }
            })
            .channel(localBackend.getChannelClass())
            .connect(localAddress);
        callback.setReturnValue(connectionFuture);
    }
}
