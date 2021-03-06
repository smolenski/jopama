package pl.rodia.jopama.integration.zookeeper;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pl.rodia.mpf.Task;

public class StartFinishDetector implements DirChangesObserver
{

	public StartFinishDetector(
			Task onStart,
			Task onFinish
	)
	{
		super();
		this.onStart = onStart;
		this.onFinish = onFinish;
	}

	@Override
	public void directoryContentChanged(
			List<String> fileNames
	)
	{
		StringBuilder dirContentStr = new StringBuilder();
		dirContentStr.append(
				"["
		);
		for (String child : fileNames)
		{
			dirContentStr.append(
					child + ","
			);
		}
		dirContentStr.append(
				"]"
		);
		logger.debug(
				"DirectoryContent: " + dirContentStr
		);
		if (
			fileNames.contains(
					START_FILE_NAME
			)
		)
		{
			if (
				this.onStart != null
			)
			{
				this.onStart.execute();
				this.onStart = null;
			}
		}
		if (
			fileNames.contains(
					FINISH_FILE_NAME
			)
		)
		{
			if (
				this.onFinish != null
			)
			{
				this.onFinish.execute();
				this.onFinish = null;
			}
		}
	}

	Task onStart;
	Task onFinish;

	static final Logger logger = LogManager.getLogger();
	static final String START_FILE_NAME = "START";
	static final String FINISH_FILE_NAME = "FINISH";
}
