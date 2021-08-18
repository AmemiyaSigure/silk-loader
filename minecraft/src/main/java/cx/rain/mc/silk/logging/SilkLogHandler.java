package cx.rain.mc.silk.logging;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.LogHandler;
import net.fabricmc.loader.impl.util.log.LogLevel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SilkLogHandler implements LogHandler {
	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean isReplayedBuiltin) {
		getLogger(category).log(translateLogLevel(level), msg, exc);
	}

	@Override
	public boolean shouldLog(LogLevel level, LogCategory category) {
		return getLogger(category).isEnabled(translateLogLevel(level));
	}

	private static Level translateLogLevel(LogLevel level) {
		switch (level) {
			case ERROR:
				return Level.ERROR;
			case WARN:
				return Level.WARN;
			case INFO:
				return Level.INFO;
			case DEBUG:
				return Level.DEBUG;
			case TRACE:
				return Level.TRACE;
		}

		throw new IllegalArgumentException("unknown log level: "+level);
	}

	private static Logger getLogger(LogCategory category) {
		Logger log = (Logger) category.data;

		if (log == null) {
			String name = category.name.isEmpty() ? Log.NAME : String.format("%s/%s", Log.NAME, category.name);
			category.data = log = LogManager.getLogger(name);
		}

		return log;
	}

	@Override
	public void close() {
	}
}