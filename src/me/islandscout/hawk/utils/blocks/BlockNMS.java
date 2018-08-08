package me.islandscout.hawk.utils.blocks;

import me.islandscout.hawk.Hawk;
import me.islandscout.hawk.utils.AABB;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public abstract class BlockNMS {

    float strength;
    private Block block;
    protected AABB aabb;
    protected boolean solid;

    BlockNMS(Block block) {
        this.block = block;
    }

    public abstract Object getNMS();

    public abstract void sendPacketToPlayer(Player p);

    public float getStrength() {
        return strength;
    }

    public Block getBukkitBlock() {
        return block;
    }

    public AABB getCollisionBox() {
        return aabb;
    }

    public static BlockNMS getBlockNMS(Block b) {
        if(Hawk.getServerVersion() == 8)
            return new BlockNMS8(b);
        else
            return new BlockNMS7(b);
    }

    //Man, I hate having to do this. I don't know why Bukkit is confused over the definition of SOLID.
    public boolean isSolid() {
        return solid;
    }
}
