package pl.rodia.jopama.integration.zookeeper;

import pl.rodia.jopama.data.ExtendedComponent;
import pl.rodia.jopama.data.ExtendedTransaction;
import pl.rodia.jopama.data.ObjectId;
import pl.rodia.jopama.integration.ExtendedData;
import pl.rodia.jopama.integration.UniversalStorageAccess;

public class ZooKeeperUniversalStorageAccess extends UniversalStorageAccess
{

	public ZooKeeperUniversalStorageAccess(
			ZooKeeperStorageAccess zooKeeperStorageAccess
	)
	{
		super();
		this.zooKeeperStorageAccess = zooKeeperStorageAccess;
	}

	@Override
	public ObjectId createComponent(
			Long id, ExtendedComponent extendedComponent
	)
	{
		assert extendedComponent.externalVersion.equals(
				new Integer(
						0
				)
		);
		ZooKeeperObjectId zooKeeperObjectId = new ZooKeeperObjectId(
				ZooKeeperObjectId.getComponentUniqueName(
						id
				)
		);
		return this.zooKeeperStorageAccess.createObject(
				zooKeeperObjectId,
				ZooKeeperHelpers.getComponentPath(zooKeeperObjectId),
				ZooKeeperHelpers.serializeComponent(
						extendedComponent.component
				)
		);
	}

	@Override
	public ObjectId createTransaction(
			Long id, ExtendedTransaction extendedTransaction
	)
	{
		assert extendedTransaction.externalVersion.equals(
				new Integer(
						0
				)
		);
		ZooKeeperObjectId zooKeeperObjectId = new ZooKeeperObjectId(
				ZooKeeperObjectId.getTransactionUniqueName(
						id
				)
		);
		return this.zooKeeperStorageAccess.createObject(
				zooKeeperObjectId,
				ZooKeeperHelpers.getTransactionPath(zooKeeperObjectId),
				ZooKeeperHelpers.serializeTransaction(
						extendedTransaction.transaction
				)
		);
	}

	ZooKeeperStorageAccess zooKeeperStorageAccess;

	@Override
	public ExtendedComponent getComponent(
			ObjectId objectId
	)
	{
		ZooKeeperObjectId zooKeeperObjectId = (ZooKeeperObjectId) objectId;
		ExtendedData extendedData = this.zooKeeperStorageAccess.readObject(
				zooKeeperObjectId,
				ZooKeeperHelpers.getComponentPath(zooKeeperObjectId)
		);
		if (
			extendedData == null
		)
		{
			return null;
		}
		else
		{
			return new ExtendedComponent(
					ZooKeeperHelpers.deserializeComponent(
							extendedData.data
					),
					extendedData.version
			);
		}
	}

	@Override
	public ExtendedTransaction getTransaction(
			ObjectId objectId
	)
	{
		ZooKeeperObjectId zooKeeperObjectId = (ZooKeeperObjectId) objectId;
		ExtendedData extendedData = this.zooKeeperStorageAccess.readObject(
				zooKeeperObjectId,
				ZooKeeperHelpers.getTransactionPath(zooKeeperObjectId)
		);
		if (
			extendedData == null
		)
		{
			return null;
		}
		else
		{
			return new ExtendedTransaction(
					ZooKeeperHelpers.deserializeTransaction(
							extendedData.data
					),
					extendedData.version
			);
		}

	}
}
