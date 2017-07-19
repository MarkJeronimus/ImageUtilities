/*
 * This file is part of ImageUtilities.
 *
 * Copyleft 2016 Mark Jeronimus. All Rights Reversed.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ImageUtilities. If not, see <http://www.gnu.org/licenses/>.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.digitalmodular.imageutilities.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A set of workers, each with an optional dependence on other workers. Workers are either in the eligible or blocked
 * state. Blocked workers wait until the workers that they depend on are finished, at which point they can become
 * eligible. When retrieving a worker, only an eligible worker will be returned. If there are none, it will block until
 * there is one.
 * <p>
 * It neither extends from {@link Set} and {@link BlockingQueue} because most of their retrieval methods have no meaning
 * in this context.
 * <p>
 * This container should have negligible cost for small numbers of workers (up to a hundred) but doesn't scale well to
 * very high numbers of workers.
 *
 * @param <V> the result type of the worker
 * @author Mark Jeronimus
 */
// Created 2015-08-28
public class DependentWorkerQueue<V> {
	private final Collection<List<Callable<V>>>    dependencyQueue = new LinkedList<>();
	private final BlockingQueue<DependentCallable> eligibleQueue   = new LinkedBlockingQueue<>();

	public synchronized void clear() {
		dependencyQueue.clear();
		eligibleQueue.clear();
	}

	/**
	 * Adds a worker with no dependencies. This worker will be immediately eligible.
	 */
	public synchronized void addWorker(Callable<V> worker) {
		eligibleQueue.add(new DependentCallable(worker));
	}

	/**
	 * Adds a worker which will only become eligible after all the dependent workers finish.
	 *
	 * @param worker       the worker
	 * @param dependencies workers that must all finish before the worker becomes eligible
	 */
	public synchronized void addWorker(Callable<V> worker, Collection<Callable<V>> dependencies) {
		List<Callable<V>> dependencyList = new ArrayList<>(64);
		dependencyList.add(worker);
		dependencyList.addAll(dependencies);
		dependencyQueue.add(dependencyList);
	}

	public synchronized boolean hasEligibleWorkers() {
		return !eligibleQueue.isEmpty();
	}

	public Callable<V> takeEligibleWorker() throws InterruptedException {
		return eligibleQueue.take();
	}

	public synchronized int size() {
		return dependencyQueue.size() + eligibleQueue.size();
	}

	public synchronized boolean isEmpty() {
		return dependencyQueue.isEmpty() && eligibleQueue.isEmpty();
	}

	/**
	 * Removes the specified worker from all blocked worker's dependencies. Any blocked worker that as a result of this
	 * process had all it's dependencies removed will be transferred to the eligible-queue.
	 *
	 * @param dependency the worker that finished, and which other workers might be waiting for.
	 */
	synchronized void releaseBlockedWorkers(Callable<V> dependency) {
		Iterator<List<Callable<V>>> iterator = dependencyQueue.iterator();
		while (iterator.hasNext()) {
			// Get the worker and it's dependencies
			List<Callable<V>> worker = iterator.next();

			// Remove our dependency
			worker.remove(dependency);

			// Transfer worker to eligible queue if it has no dependencies left
			if (worker.size() == 1) {
				iterator.remove();
				eligibleQueue.offer(new DependentCallable(worker.get(0)));
			}
		}
	}

	private final class DependentCallable implements Callable<V> {
		private final Callable<V> worker;

		private DependentCallable(Callable<V> worker) {
			this.worker = worker;
		}

		@Override
		public V call() throws Exception {
			V result = worker.call();
			releaseBlockedWorkers(worker);
			return result;
		}
	}
}
