package org.cyclop.service.queryprotocoling.impl;

import org.cyclop.model.CqlQuery;
import org.cyclop.model.CqlQueryName;
import org.cyclop.model.QueryEntry;
import org.cyclop.model.QueryHistory;
import org.cyclop.model.UserIdentifier;
import org.cyclop.service.common.FileStorage;
import org.cyclop.test.AbstractTestCase;
import org.cyclop.test.ThreadTestScope;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** @author Maciej Miklas */
public class TestHistoryService extends AbstractTestCase {

	public static String CR = System.getProperty("line.separator");

	@Inject
	private HistoryServiceImpl historyService;

	@Inject
	private AsyncFileStore<QueryHistory> asyncFileStore;

	private UserIdentifier user;

	@Inject
	private FileStorage storage;

	@Inject
	private ThreadTestScope threadTestScope;

	@After
	public void cleanUp() {
		threadTestScope.setSingleThread(false);
	}

	@Before
	public void setup() {
		asyncFileStore.flush();
		QueryHistory history = historyService.readHistory();
		assertNotNull(history);
		history.clear();

		assertEquals(0, history.size());

		user = historyService.getUser();
		assertNotNull(user);
		assertNotNull(user.id);
	}

	@Test
	public void testCreateReadAndClear() throws Exception {
		QueryHistory history = historyService.readHistory();

		for (int i = 0; i < 600; i++) {
			history.add(new QueryEntry(
					new CqlQuery(CqlQueryName.SELECT, "select * " + CR + "from HistoryTest where " + CR + "id=" + i)));
			historyService.store(history);
			QueryHistory historyQueue = asyncFileStore.getFromWriteQueue(user);
			assertNotNull(historyQueue);

			// should be the same instance
			assertSame(history, historyQueue);
		}
		assertEquals(500, history.size());

		assertNull(storage.read(user, QueryHistory.class));

		asyncFileStore.flush();
		assertNull(asyncFileStore.getFromWriteQueue(user));

		assertSame(history, historyService.readHistory());

		QueryHistory readHist = storage.read(user, QueryHistory.class);
		assertNotSame(history, readHist);

		for (int i = 100; i < 600; i++) {
			QueryEntry tofind = new QueryEntry(
					new CqlQuery(CqlQueryName.SELECT, "select * from HistoryTest where id=" + i));
			assertTrue(tofind + " NOT FOUND IN: " + readHist, readHist.contains(tofind));
		}

		{
			history.clear();
			assertEquals(0, history.size());
			historyService.store(history);
			asyncFileStore.flush();
			assertEquals(0, storage.read(user, QueryHistory.class).size());
		}
	}

	@Test
	public void testMultiThreadForMultipleUsers() throws Exception {
		threadTestScope.setSingleThread(false);

		Set<QueryHistory> histories = executeMultiThreadTest(300);
		assertEquals(3, histories.size());
	}

	@Test
	public void testMultiThreadForSingleUsers() throws Exception {
		threadTestScope.setSingleThread(true);

		Set<QueryHistory> histories = executeMultiThreadTest(100);
		assertEquals(1, histories.size());
	}

	public Set<QueryHistory> executeMultiThreadTest(final int repeatInTest) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(3);
		final Set<QueryHistory> histories = Collections.synchronizedSet(new HashSet<QueryHistory>());

		List<Callable<Void>> tasks = new ArrayList<>(3);
		final AtomicInteger executedCount = new AtomicInteger(0);
		for (int i = 0; i < 3; i++) {
			tasks.add(new Callable<Void>() {

				@Override
				public Void call() throws Exception {
					for (int i = 0; i < repeatInTest; i++) {
						QueryHistory history = historyService.readHistory();
						histories.add(history);

						QueryEntry histEntry = new QueryEntry(new CqlQuery(CqlQueryName.SELECT,
								"select * from MyTable2 where id=" + UUID.randomUUID()));
						history.add(histEntry);

						verifyHistEntry(history, histEntry);

						historyService.store(history);
						if (i % 20 == 0) {
							asyncFileStore.flush();
						}

						QueryHistory readHist = historyService.readHistory();
						verifyHistEntry(readHist, histEntry);

						executedCount.incrementAndGet();
						assertEquals(0, storage.getLockRetryCount());
					}
					return null;
				}

				void verifyHistEntry(QueryHistory history, QueryEntry histEntry) {
					assertNotNull(history);

					assertTrue("History (" + executedCount + "):" + histEntry + " not found in: " + history,
							history.contains(histEntry));
				}
			});
		}

		List<Future<Void>> results = executor.invokeAll(tasks);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.MINUTES);

		for (Future<Void> result : results) {
			result.get();
		}
		assertEquals(3 * repeatInTest, executedCount.get());
		return histories;
	}

}
/*

*/