package latmod.ftbu.api.readme;

import latmod.lib.FastMap;

public class ReadmeFile
{
	public final FastMap<String, ReadmeCategory> map = new FastMap<String, ReadmeCategory>();
	
	public ReadmeCategory get(String s)
	{
		ReadmeCategory c = map.get(s);
		if(c == null) add(c = new ReadmeCategory(s));
		return c;
	}
	
	public void add(ReadmeCategory c)
	{ map.put(c.name, c); }
}