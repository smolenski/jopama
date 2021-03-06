package pl.rodia.jopama.integration.zookeeper;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pl.rodia.jopama.data.Component;
import pl.rodia.jopama.data.Serializer;
import pl.rodia.jopama.data.Transaction;

public class ZooKeeperHelpers
{

	static String getTransactionBasePath()
	{
		return "/Transactions";
	}

	static String getComponentBasePath()
	{
		return "/Components";
	}

	static String getPath(
			String basePath,
			ZooKeeperObjectId objectId
	)
	{
		return basePath + "/" + objectId.uniqueName;
	}

	static ZooKeeperObjectId getIdFromPath(
			String path
	)
	{
		String[] components = path.split(
				"/"
		);
		if (
			components.length != 3 || components[0].equals(
					""
			) == false
		)
		{
			throw new IllegalArgumentException(
					"path: " + path + " splitted: " + Arrays.toString(
							components
					)
			);
		}
		String fileName = components[2];
		return new ZooKeeperObjectId(
				fileName
		);
	}

	static String getTransactionPath(
			ZooKeeperObjectId objectId
	)
	{
		return ZooKeeperHelpers.getPath(
				getTransactionBasePath(),
				objectId
		);
	}

	static String getComponentPath(
			ZooKeeperObjectId objectId
	)
	{
		return ZooKeeperHelpers.getPath(
				getComponentBasePath(),
				objectId
		);
	}

	static byte[] serializeTransaction(
			Transaction transaction
	)
	{
		try
		{
			return Serializer.serializeTransaction(
					transaction
			);
		}
		catch (IOException e)
		{
			logger.error(
					"serializeTransaction failed, transaction: " + transaction + " error: " + e
			);
			assert false;
			return null;
		}
	}

	static Transaction deserializeTransaction(
			byte[] data
	)
	{
		try
		{
			return Serializer.deserializeTransaction(
					data
			);
		}
		catch (ClassNotFoundException | IOException e)
		{
			logger.error(
					"deserializeTransaction failed, error: " + e
			);
			assert false;
			return null;
		}
	}

	static byte[] serializeComponent(
			Component component
	)
	{
		try
		{
			return Serializer.serializeComponent(
					component
			);
		}
		catch (IOException e)
		{
			logger.error(
					"serializeComponent failed, component: " + component + " error: " + e
			);
			assert false;
			return null;
		}
	}

	static Component deserializeComponent(
			byte[] data
	)
	{
		try
		{
			return Serializer.deserializeComponent(
					data
			);
		}
		catch (ClassNotFoundException | IOException e)
		{
			logger.error(
					"deserializeComponent failed, error: " + e
			);
			assert false;
			return null;
		}
	}

	static List<String> prepareZooKeeperClustersConnectionStrings(
			String addresses, Integer zooKeeperClusterSize
	)
	{
		List<String> splittedAddresses = Arrays.asList(
				addresses.split(
						","
				)
		);
		assert (splittedAddresses.size() % zooKeeperClusterSize) == 0;
		Integer numZooKeeperClusters = splittedAddresses.size() / zooKeeperClusterSize;
		assert numZooKeeperClusters > 0;
		List<String> result = new LinkedList<String>();
		for (int i = 0; i < numZooKeeperClusters; ++i)
		{
			Integer splittedAddressesClusterOffset = i * zooKeeperClusterSize;
			StringBuilder zooKeeperConnectionString = new StringBuilder();
			for (int j = 0; j < zooKeeperClusterSize; ++j)
			{
				if (
					j != 0
				)
				{
					zooKeeperConnectionString.append(
							","
					);
				}
				zooKeeperConnectionString.append(
						splittedAddresses.get(
								splittedAddressesClusterOffset + j
						)
				);
			}
			result.add(
					zooKeeperConnectionString.toString()
			);
		}
		return result;
	}

	static final Logger logger = LogManager.getLogger();
}
