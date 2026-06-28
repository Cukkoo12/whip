package com.cukkoo.whip.network;

import com.cukkoo.whip.WhipMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WhipAttackPacket {

    public enum SwingType {
        OVERHEAD,
        SWEEP
    }

    private final SwingType swingType;
    private final int chargeTicks;

    public WhipAttackPacket(SwingType swingType, int chargeTicks) {
        this.swingType = swingType;
        this.chargeTicks = chargeTicks;
    }

    public SwingType getSwingType() {
        return swingType;
    }

    public int getChargeTicks() {
        return chargeTicks;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(swingType);
        buf.writeVarInt(chargeTicks);
    }

    public static WhipAttackPacket decode(FriendlyByteBuf buf) {
        return new WhipAttackPacket(
                buf.readEnum(SwingType.class),
                buf.readVarInt()
        );
    }

    public static void handle(WhipAttackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                WhipMod.ServerEvents.processAttack(player, packet.getSwingType(), packet.getChargeTicks());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
