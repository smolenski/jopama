package pl.rodia.jopama.core;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pl.rodia.jopama.data.ExtendedComponent;
import pl.rodia.jopama.data.ExtendedTransaction;
import pl.rodia.jopama.data.ObjectId;
import pl.rodia.jopama.data.UnifiedAction;
import pl.rodia.jopama.gateway.ErrorCode;
import pl.rodia.jopama.gateway.NewComponentVersionFeedback;
import pl.rodia.jopama.gateway.NewTransactionVersionFeedback;
import pl.rodia.jopama.gateway.RemoteStorageGateway;
import pl.rodia.jopama.stats.AsyncOperationsCounters;
import pl.rodia.jopama.stats.OperationCounter;
import pl.rodia.jopama.stats.StatsResult;
import pl.rodia.jopama.stats.StatsSyncSource;
import pl.rodia.mpf.Task;
import pl.rodia.mpf.TaskRunner;

public class TransactionProcessorImpl extends TransactionProcessor implements StatsSyncSource
{
	
	class TransactionEntry
	{
		public TransactionEntry(
				Task done, Long startTimeMs
		)
		{
			super();
			this.done = done;
			this.startTimeMs = startTimeMs;
		}
		Task done;
		Long startTimeMs;
	};

	public TransactionProcessorImpl(
			TaskRunner taskRunner,
			TransactionAnalyzer transactionAnalyzer,
			LocalStorage storage,
			RemoteStorageGateway storageGateway
	)
	{
		super();
		this.taskRunner = taskRunner;
		this.transactionAnalyzer = transactionAnalyzer;
		this.storage = storage;
		this.storageGateway = storageGateway;
		this.transactions = new HashMap<ObjectId, TransactionEntry>();
		this.scheduledProcessingTaskId = null;
		this.noActionCounter = new OperationCounter(this.taskRunner.name + "::NoActionCounter");
		this.transactionProcessingCounters = new AsyncOperationsCounters(this.taskRunner.name + "::TransactionProcessing");
		this.transactionUpdated = new SuccessFailureCounter(this.taskRunner.name + "::TransactionUpdated");
		this.componentUpdated = new SuccessFailureCounter(this.taskRunner.name + "::ComponentUpdated");
		this.scheduleProcessing();
	}

	public void addTransaction(
			ObjectId transactionId,
			Task transactionDone
	)
	{
		this.transactionProcessingCounters.onRequestStarted();
		this.transactions.put(
				transactionId,
				new TransactionEntry(
					transactionDone,
					System.currentTimeMillis()
				)
		);
		if (
			this.scheduledProcessingTaskId == null
		)
		{
			this.scheduleProcessing();
		}
		this.processTransaction(
				transactionId
		);
	}

	void removeTransaction(
			ObjectId transactionId
	)
	{
		TransactionEntry transactionEntry = this.transactions.remove(
				transactionId
		);
		if (
			transactionEntry != null
		)
		{
			if (
				this.transactions.isEmpty()
			)
			{
				if (
					this.taskRunner.cancelTask(
							this.scheduledProcessingTaskId
					).equals(
							new Boolean(
									true
							)
					)
				)
				{
					logger.debug(
							"executeScheduledProcessing - cancelled"
					);
					this.scheduledProcessingTaskId = null;
				}
			}
			Long transactionDuration = System.currentTimeMillis() - transactionEntry.startTimeMs;
			this.transactionProcessingCounters.onRequestFinished(transactionDuration);
			transactionEntry.done.execute();
		}
	}

	public void executeScheduledProcessing()
	{
		logger.debug(
				"executeScheduledProcessing"
		);
		this.scheduledProcessingTaskId = null;
		for (Map.Entry<ObjectId, TransactionEntry> entry : this.transactions.entrySet())
		{
			this.processTransaction(
					entry.getKey()
			);
		}
		this.scheduleProcessing();
	}

	public void scheduleProcessing()
	{
		logger.debug(
				"executeScheduledProcessing - scheduling"
		);
		if (
			this.transactions.isEmpty() == true
		)
		{
			return;
		}
		assert(this.scheduledProcessingTaskId == null);
		this.scheduledProcessingTaskId = this.taskRunner.schedule(
				new Task()
				{
					@Override
					public void execute()
					{
						executeScheduledProcessing();
					}
				},
				new Long(
						3 * 1000
				)
		);
	}

	public void processTransaction(
			ObjectId transactionId
	)
	{
		if (
			this.transactions.containsKey(
					transactionId
			) == false
		)
		{
			return;
		}
		logger.debug(
				this.taskRunner.name + ":getting change"
		);
		UnifiedAction change = this.transactionAnalyzer.getChange(
				transactionId
		);
		if (
			change != null
		)
		{
			if (
				change.componentChange != null
			)
			{
				logger.debug(
						this.taskRunner.name + ":changeComponent, componentId: " + change.componentChange.componentId + " CURRENT: "
								+ change.componentChange.currentVersion + " NEXT: "
								+ change.componentChange.nextVersion
				);
				this.storageGateway.changeComponent(
						change.componentChange,
						this.createNewComponentVersionHandler(
								change.componentChange.transactionId,
								change.componentChange.componentId
						)
				);
			}
			if (
				change.transactionChange != null
			)
			{
				logger.debug(
						this.taskRunner.name + ":changeTransaction, transactionId: " + change.transactionChange.transactionId + " CURRENT: "
								+ change.transactionChange.currentVersion + " NEXT: "
								+ change.transactionChange.nextVersion
				);
				this.storageGateway.changeTransaction(
						change.transactionChange,
						this.createNewTransactionVersionHandler(
								change.transactionChange.transactionId
						)
				);
			}
			if (
				change.downloadRequest != null
			)
			{
				logger.debug(
						this.taskRunner.name + ":downloadRequest" + " transactionId: " + change.downloadRequest.transactionId
								+ " componentId: " + change.downloadRequest.componentId
				);
				assert change.downloadRequest.transactionId != null;
				this.storageGateway.requestTransaction(
						change.downloadRequest.transactionId,
						this.createNewTransactionVersionHandler(
								change.downloadRequest.transactionId
						)
				);
				if (
					change.downloadRequest.componentId != null
				)
				{
					this.storageGateway.requestComponent(
							change.downloadRequest.componentId,
							this.createNewComponentVersionHandler(
									change.downloadRequest.transactionId,
									change.downloadRequest.componentId
							)
					);
				}
			}
		}
		else
		{
			this.noActionCounter.increase();
			logger.debug(
					this.taskRunner.name + ":getChange - null"
			);
		}
	}

	private NewComponentVersionFeedback createNewComponentVersionHandler(
		ObjectId transactionId,
		ObjectId componentId
	)
	{
		return new NewComponentVersionFeedback()
		{
			@Override
			public void success(
					ExtendedComponent extendedComponent
			)
			{
				assert (extendedComponent != null);
				if (
					storage.putComponent(
							componentId,
							extendedComponent
					)
				)
				{
					componentUpdated.noticeSuccess();
					processTransaction(
							transactionId
					);
				}
				else
				{
					componentUpdated.noticeFailure();
				}
			}

			@Override
			public void failure(
					ErrorCode errorCode
			)
			{
				componentUpdated.noticeFailure();
				logger.info("Failure, transactionId: " + transactionId + " componentId: " + componentId + " errorCode: " + errorCode);
				assert errorCode != ErrorCode.NOT_EXISTS;
			}
		};
	}

	private NewTransactionVersionFeedback createNewTransactionVersionHandler(
			ObjectId transactionId
	)
	{
		return new NewTransactionVersionFeedback()
		{
			@Override
			public void success(
					ExtendedTransaction extendedTransaction
			)
			{
				assert (extendedTransaction != null);
				if (
					storage.putTransaction(
							transactionId,
							extendedTransaction
					)
				)
				{
					transactionUpdated.noticeSuccess();
					processTransaction(
							transactionId
					);
				}
				else
				{
					transactionUpdated.noticeFailure();
				}
			}

			@Override
			public void failure(
					ErrorCode errorCode
			)
			{
				transactionUpdated.noticeFailure();
				if (
					errorCode == ErrorCode.NOT_EXISTS
				)
				{
					removeTransaction(
							transactionId
					);
				}
			}
		};
	}

	@Override
	public StatsResult getStats()
	{
		StatsResult result = new StatsResult();
		result.addSamples(this.transactionProcessingCounters.getStats());
		result.addSamples(this.noActionCounter.getStats());
		result.addSamples(this.transactionUpdated.getStats());
		result.addSamples(this.componentUpdated.getStats());
		return result;
	}
	
	TaskRunner taskRunner;
	TransactionAnalyzer transactionAnalyzer;
	LocalStorage storage;
	RemoteStorageGateway storageGateway;
	Map<ObjectId, TransactionEntry> transactions;
	Integer scheduledProcessingTaskId;
	OperationCounter noActionCounter;
	AsyncOperationsCounters transactionProcessingCounters;
	SuccessFailureCounter transactionUpdated;
	SuccessFailureCounter componentUpdated;
	static final Logger logger = LogManager.getLogger();
}
