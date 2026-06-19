package io.mehdieidi.modless.platform.model.application;

import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Provides per-model locks for operations that mutate model JSON or XMI sidecars. */
public final class ModelLockService {

  /** Default wait time for acquiring a model lock. */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /** Locks keyed by model id. */
  private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  /**
   * Executes an operation while holding the lock for the requested model id.
   *
   * @param modelId model identifier used as the lock key
   * @param timeout maximum time to wait for the lock; {@code null} uses the default
   * @param operation operation to execute while locked
   * @param <T> operation result type
   * @return operation result
   */
  public <T> T withModelLock(String modelId, Duration timeout, Callable<T> operation) {
    String key = modelId == null || modelId.isBlank() ? "__unknown__" : modelId;
    ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
    boolean acquired = false;
    try {
      Duration wait = timeout == null ? DEFAULT_TIMEOUT : timeout;
      acquired = lock.tryLock(wait.toMillis(), TimeUnit.MILLISECONDS);
      if (!acquired) {
        throw new PlatformException(409, "Model is busy. Retry the operation later.");
      }
      return operation.call();
    } catch (PlatformException ex) {
      throw ex;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new PlatformException(503, "Model operation was interrupted.");
    } catch (Exception ex) {
      if (ex instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new PlatformException(500, "Model operation failed.");
    } finally {
      if (acquired) {
        lock.unlock();
      }
      if (!lock.hasQueuedThreads() && !lock.isLocked()) {
        locks.remove(key, lock);
      }
    }
  }
}
