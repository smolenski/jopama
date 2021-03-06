package pl.rodia.jopama.data;

import java.io.Serializable;

public class Component implements Serializable
{
	public Component(
			Integer version,
			ObjectId owner,
			Integer value,
			Integer newValue
	)
	{
		super();
		this.version = version;
		this.owner = owner;
		this.value = value;
		this.newValue = newValue;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((newValue == null) ? 0 : newValue.hashCode());
		result = prime * result + ((owner == null) ? 0 : owner.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(
			Object obj
	)
	{
		if (
			this == obj
		)
		{
			return true;
		}
		if (
			obj == null
		)
		{
			return false;
		}
		if (
			!(obj instanceof Component)
		)
		{
			return false;
		}
		Component other = (Component) obj;
		if (
			newValue == null
		)
		{
			if (
				other.newValue != null
			)
			{
				return false;
			}
		}
		else if (
			!newValue.equals(
					other.newValue
			)
		)
		{
			return false;
		}
		if (
			owner == null
		)
		{
			if (
				other.owner != null
			)
			{
				return false;
			}
		}
		else if (
			!owner.equals(
					other.owner
			)
		)
		{
			return false;
		}
		if (
			value == null
		)
		{
			if (
				other.value != null
			)
			{
				return false;
			}
		}
		else if (
			!value.equals(
					other.value
			)
		)
		{
			return false;
		}
		if (
			version == null
		)
		{
			if (
				other.version != null
			)
			{
				return false;
			}
		}
		else if (
			!version.equals(
					other.version
			)
		)
		{
			return false;
		}
		return true;
	}

	@Override
	public String toString()
	{
		return "(" + "version: " + this.version + ", owner: " + this.owner + ", value: " + this.value + ", newValue: " + this.newValue + ")";
	}

	public Integer version;
	public ObjectId owner;
	public Integer value;
	public Integer newValue;
	private static final long serialVersionUID = 3059455288161614021L;
}
