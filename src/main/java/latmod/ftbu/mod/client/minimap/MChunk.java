package latmod.ftbu.mod.client.minimap;

import java.util.List;

import cpw.mods.fml.relauncher.*;
import latmod.ftbu.world.*;
import latmod.lib.*;
import net.minecraft.world.World;

@SideOnly(Side.CLIENT)
public class MChunk
{
	public final MArea area;
	public final int posX, posY, rposX, rposY;
	public final int[] pixels;
	public ChunkType type;
	public LMPlayerClient owner;
	public ThreadReloadChunk thread = null;
	
	public MChunk(MArea a, int x, int y)
	{
		area = a;
		posX = x;
		posY = y;
		rposX = MathHelperLM.wrap(posX, MArea.size_c);
		rposY = MathHelperLM.wrap(posY, MArea.size_c);
		pixels = new int[256];
		type = ChunkType.UNLOADED;
		owner = null;
	}
	
	public void setType(int i)
	{
		type = ChunkType.WILDERNESS;
		owner = null;
		
		if(i > 0)
		{
			owner = LMWorldClient.inst.getPlayer(i);
			if(owner == null) type = ChunkType.CLAIMED_OTHER;
			else if(owner.equalsPlayer(LMWorldClient.inst.clientPlayer)) type = ChunkType.CLAIMED_SELF;
			else type = owner.isFriend(LMWorldClient.inst.clientPlayer) ? ChunkType.CLAIMED_FRIEND : ChunkType.CLAIMED_OTHER;
		}
		else type = ChunkType.VALUES[MathHelperLM.clampInt(-i, 0, ChunkType.VALUES.length - 1)];
		
		area.isDirty = true;
	}
	
	public int getTypeID()
	{
		if(owner != null)
			return owner.playerID;
		return -type.ID;
	}
	
	public void setPixel(int x, int y, int col)
	{
		x = MathHelperLM.wrap(x, 16);
		y = MathHelperLM.wrap(y, 16);
		col = LMColorUtils.getRGBA(col, 255);
		if(pixels[x + y * 16] != col) area.isDirty = true;
		pixels[x + y * 16] = col;
	}
	
	public void reload(World w)
	{
		if(thread != null) thread = null;
		thread = new ThreadReloadChunk(w, this);
		thread.start();
	}
	
	@SideOnly(Side.CLIENT)
	public void getMessage(List<String> l, boolean shift)
	{
		if(type != null)
		{
			if(owner != null && type.isClaimed())
				l.add(type.chatColor + owner.getName());
			else
				l.add(type.chatColor + type.getIDS());
		}
	}
	
	public static short getIndexC(int cx, int cy)
	{
		cx = MathHelperLM.wrap(cx, MArea.size_c);
		cy = MathHelperLM.wrap(cy, MArea.size_c);
		return Bits.bytesToShort(cx, cy);
	}
}