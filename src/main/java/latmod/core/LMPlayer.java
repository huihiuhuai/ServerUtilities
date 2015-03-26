package latmod.core;

import java.util.UUID;

import latmod.core.cmd.CommandLM;
import latmod.core.event.LMPlayerEvent;
import latmod.core.mod.LC;
import latmod.core.net.*;
import latmod.core.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import cpw.mods.fml.relauncher.Side;

public class LMPlayer implements Comparable<LMPlayer>
{
	public static final String ACTION_LOGGED_IN = "Login";
	public static final String ACTION_LOGGED_OUT = "Logout";
	public static final String ACTION_GROUPS_CHANGED = "Groups";
	
	public static class Group
	{
		public final LMPlayer owner;
		public final int groupID;
		public String name;
		public final FastList<LMPlayer> members;
		
		public Group(LMPlayer p, int id, String s)
		{
			owner = p;
			groupID = id;
			name = (s == null) ? "Unnamed" : s;
			members = new FastList<LMPlayer>();
		}
		
		public boolean equals(Object o)
		{
			if(o == null) return false;
			if(o == this) return true;
			if(o instanceof Group) return equals(o.toString());
			if(o instanceof String) return o.equals(name);
			if(o instanceof Integer) return groupID == ((Integer)o).intValue();
			return false;
		}
		
		public String toString()
		{ return name; }
		
		public int hashCode()
		{ return groupID; }
	}
	
	public final int playerID;
	public final UUID uuid;
	public final String username;
	
	private final String uuidString;
	public final FastList<LMPlayer> friends;
	public final FastList<Group> groups;
	public final ItemStack[] lastArmor;
	
	public NBTTagCompound customData;
	private boolean isOnline;
	public boolean isOld;
	public int lastGroupID = 0;
	public final NBTTagCompound tempData = new NBTTagCompound();
	
	public LMPlayer(int i, UUID id, String s)
	{
		playerID = i;
		uuid = id;
		username = s;
		
		uuidString = uuid.toString();
		friends = new FastList<LMPlayer>();
		groups = new FastList<Group>();
		customData = new NBTTagCompound();
		lastArmor = new ItemStack[5];
	}
	
	public EntityPlayerMP getPlayerMP()
	{ return LatCoreMC.getAllOnlinePlayers().get(uuid); }
	
	public EntityPlayer getPlayerSP()
	{
		World w = LC.proxy.getClientWorld();
		if(w != null) return w.func_152378_a(uuid);
		return null;
	}
	
	public boolean isOnline()
	{ return isOnline; }
	
	public void setOnline(boolean b)
	{ isOnline = b; }
	
	public void sendUpdate(String action, boolean clientUpdate)
	{
		if(LatCoreMC.isServer() && action != null && !action.isEmpty())
		{
			if(action.equals(ACTION_LOGGED_IN))
				new LMPlayerEvent.LoggedIn(this, Side.SERVER, getPlayerMP(), !isOld).post();
			else if(action.equals(ACTION_LOGGED_IN))
				new LMPlayerEvent.LoggedOut(this, Side.SERVER, getPlayerMP()).post();
			
			new LMPlayerEvent.DataChanged(this, Side.SERVER, action).post();
			if(clientUpdate) MessageLM.NET.sendToAll(new MessageUpdateLMPlayer(this, action.equals(ACTION_LOGGED_IN), action));
		}
	}
	
	public void sendUpdate(String action)
	{ sendUpdate(action, true); }
	
	public void receiveUpdate(String action)
	{
		EntityPlayer ep = getPlayerSP();
		
		if(action.equals(ACTION_LOGGED_IN))
			new LMPlayerEvent.LoggedIn(this, Side.CLIENT, ep, !isOld).post();
		
		if(action.equals(ACTION_LOGGED_OUT))
			new LMPlayerEvent.LoggedOut(this, Side.CLIENT, ep).post();
		
		new LMPlayerEvent.DataChanged(this, Side.CLIENT, action).post();
	}
	
	public boolean isFriendRaw(LMPlayer p)
	{ return p != null && (playerID == p.playerID || friends.contains(p.playerID)); }
	
	public boolean isFriend(LMPlayer p)
	{ return isFriendRaw(p) && p.isFriendRaw(this); }
	
	// NBT reading / writing
	
	public void readFromNBT(NBTTagCompound tag)
	{
		isOnline = tag.getBoolean("On");
		
		friends.clear();
		
		int[] fl = tag.getIntArray("Friends");
		
		if(fl != null && fl.length > 0)
		for(int j = 0; j < fl.length; j++)
		{
			LMPlayer p = getPlayer(fl[j]);
			if(p != null) friends.add(p);
		}
		
		lastGroupID = tag.getInteger("GID");
		groups.clear();
		
		NBTTagList gl = (NBTTagList)tag.getTag("Groups");
		
		if(gl != null) for(int i = 0; i < gl.tagCount(); i++)
		{
			NBTTagCompound tag1 = gl.getCompoundTagAt(i);
			int id = tag1.getInteger("ID");
			String name = tag1.getString("N");
			int[] m = tag1.getIntArray("M");
			
			Group g = new Group(this, id, name);
			
			for(int j = 0; j < m.length; j++)
			{
				LMPlayer p = getPlayer(m[j]);
				if(p != null) g.members.add(p);
			}
			
			groups.add(g);
		}
		
		customData = tag.getCompoundTag("CustomData");
		
		if(customData.hasKey("IsOld"))
		{
			tag.setBoolean("Old", customData.getBoolean("IsOld"));
			customData.removeTag("IsOld");
		}
		
		isOld = !tag.getBoolean("NewPlayer");
		
		InvUtils.readItemsFromNBT(lastArmor, tag, "LastItems");
	}
	
	public void writeToNBT(NBTTagCompound tag)
	{
		if(!isOld) tag.setBoolean("NewPlayer", true);
		
		if(isOnline) tag.setBoolean("On", isOnline);
		
		if(!friends.isEmpty())
		{
			int[] m = new int[friends.size()];
			
			if(m.length > 0)
			{
				for(int j = 0; j < m.length; j++)
					m[j] = friends.get(j).playerID;
				
				tag.setIntArray("Friends", m);
			}
		}
		
		if(lastGroupID > 0) tag.setInteger("GID", lastGroupID);
		
		if(groups.size() > 0)
		{
			NBTTagList tag1 = new NBTTagList();
			
			for(int i = 0; i < groups.size(); i++)
			{
				Group g = groups.get(i);
				
				NBTTagCompound tag2 = new NBTTagCompound();
				tag2.setByte("ID", (byte)g.groupID);
				tag2.setString("N", g.name);
				
				int[] m = new int[g.members.size()];
				
				if(m.length > 0)
				{
					for(int j = 0; j < m.length; j++)
						m[j] = g.members.get(j).playerID;
					
					tag2.setIntArray("M", m);
				}
				
				tag1.appendTag(tag2);
			}
			
			tag.setTag("Groups", tag1);
		}
		
		tag.setTag("CustomData", customData);
		
		InvUtils.writeItemsToNBT(lastArmor, tag, "LastItems");
	}
	
	public int compareTo(LMPlayer o)
	{ return Integer.compare(playerID, o.playerID); }
	
	public String toString()
	{ return username; }
	
	public int hashCode()
	{ return playerID; }
	
	public boolean equals(Object o)
	{
		if(o == null) return false;
		else if(o == this) return true;
		else if(o instanceof Integer) return ((Integer)o).intValue() == playerID;
		else if(o instanceof UUID) return ((UUID)o).equals(uuid);
		else if(o instanceof EntityPlayer) return ((EntityPlayer)o).getUniqueID().equals(uuid);
		else if(o instanceof LMPlayer) return playerID == o.hashCode();
		else if(o instanceof String) return username.equalsIgnoreCase(o.toString()) || uuidString.equalsIgnoreCase(o.toString());
		else return false;
	}
	
	public boolean isOP()
	{ return LatCoreMC.getServer().func_152358_ax().func_152652_a(uuid) != null; }
	
	public CommandLM.NameType getNameType()
	{ return isOnline() ? CommandLM.NameType.ON : CommandLM.NameType.OFF; }
	
	// Static //
	
	public static final FastMap<Integer, LMPlayer> map = new FastMap<Integer, LMPlayer>();
	
	public static LMPlayer getPlayer(Object o)
	{
		if(o == null || o instanceof FakePlayer) return null;
		if(o instanceof LMPlayer) return map.get(o.hashCode());
		if(o instanceof Integer) return map.get(o);
		return map.values.getObj(o);
	}
	
	public static String[] getAllNames(CommandLM.NameType type)
	{
		if(type == CommandLM.NameType.NONE) return new String[0];
		
		FastList<String> allOn = new FastList<String>();
		FastList<String> allOff = new FastList<String>();
		
		for(int i = 0; i < map.values.size(); i++)
		{
			LMPlayer p = map.values.get(i);
			
			String s = LatCoreMC.removeFormatting(p.username);
			
			if(p.isOnline()) allOn.add(s);
			else if(!type.isOnline()) allOff.add(s);
		}
		
		allOn.sort(null);
		
		if(!type.isOnline())
		{
			allOff.sort(null);
			
			for(int i = 0; i < allOff.size(); i++)
			{
				String s = allOff.get(i);
				if(!allOn.contains(s)) allOn.add(s);
			}
		}
		
		return allOn.toArray(new String[0]);
	}
	
	public Group getGroup(int id)
	{
		if(id < 1 || id >= lastGroupID)
			return null;
		return groups.getObj(id);
	}
	
	public Group getGroup(String name)
	{
		if(name == null || name.isEmpty())
			return null;
		return groups.getObj(name);
	}
	
	public int getGroupID(String name)
	{
		Group g = getGroup(name);
		return (g == null) ? 0 : g.groupID;
	}
	
	public FastList<Group> getGroupsFor(Object o)
	{
		FastList<Group> l = new FastList<Group>();
		
		if(o == null || o instanceof FakePlayer) return l;
		
		for(int i = 0; i < groups.size(); i++)
		{
			Group g = groups.get(i);
			if(g.members.contains(o))
				l.add(g);
		}
		
		return l;
	}
	
	public boolean isPlayerInGroup(String g, Object o)
	{ FastList<Group> l = getGroupsFor(o); return l.contains(g); }

	public String[] getAllGroups()
	{
		String[] s = new String[groups.size()];
		for(int i = 0; i < s.length; i++)
			s[i] = groups.get(i).name;
		return s;
	}
}