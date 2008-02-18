/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.channel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.integration.message.Message;

/**
 * Base class for {@link MessageChannel} implementations providing common
 * properties such as the channel name and {@link DispatcherPolicy}. Also
 * provides the common functionality for sending and receiving
 * {@link Message Messages} including the invocation of any
 * {@link ChannelInterceptor ChannelInterceptors}.
 * 
 * @author Mark Fisher
 */
public abstract class AbstractMessageChannel implements MessageChannel, BeanNameAware {

	private String name;

	private final ChannelInterceptorList interceptors = new ChannelInterceptorList();

	private final DispatcherPolicy dispatcherPolicy;


	/**
	 * Create a channel with the given dispatcher policy.
	 */
	public AbstractMessageChannel(DispatcherPolicy dispatcherPolicy) {
		this.dispatcherPolicy = dispatcherPolicy;
	}


	/**
	 * Set the name of this channel.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Return the name of this channel.
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Set the name of this channel to its bean name. This will be invoked
	 * automatically whenever the channel is configured explicitly with a bean
	 * definition.
	 */
	public void setBeanName(String beanName) {
		this.setName(beanName);
	}

	/**
	 * Set the list of channel interceptors. This will clear any existing
	 * interceptors.
	 */
	public void setInterceptors(List<ChannelInterceptor> interceptors) {
		this.interceptors.set(interceptors);
	}

	/**
	 * Add a channel interceptor to the end of the list.
	 */
	public void addInterceptor(ChannelInterceptor interceptor) {
		this.interceptors.add(interceptor);
	}

	/**
	 * Return the dispatcher policy for this channel.
	 */
	public DispatcherPolicy getDispatcherPolicy() {
		return this.dispatcherPolicy;
	}

	/**
	 * Send a message on this channel. If the channel is at capacity, this
	 * method will block until either space becomes available or the sending
	 * thread is interrupted.
	 * 
	 * @param message the Message to send
	 * 
	 * @return <code>true</code> if the message is sent successfully or
	 * <code>false</code> if the sending thread is interrupted.
	 */
	public final boolean send(Message message) {
		return this.send(message, -1);
	}

	/**
	 * Send a message on this channel. If the channel is at capacity, this
	 * method will block until either the timeout occurs or the sending thread
	 * is interrupted. If the specified timeout is 0, the method will return
	 * immediately. If less than zero, it will block indefinitely (see
	 * {@link #send(Message)}).
	 * 
	 * @param message the Message to send
	 * @param timeout the timeout in milliseconds
	 * 
	 * @return <code>true</code> if the message is sent successfully,
	 * <code>false</code> if the message cannot be sent within the allotted
	 * time or the sending thread is interrupted.
	 */
	public final boolean send(Message message, long timeout) {
		if (!this.interceptors.preSend(message, this)) {
			return false;
		}
		boolean sent = this.doSend(message, timeout);
		this.interceptors.postSend(message, this, sent);
		return sent;
	}

	/**
	 * Receive the first available message from this channel. If the channel
	 * contains no messages, this method will block.
	 * 
	 * @return the first available message or <code>null</code> if the
	 * receiving thread is interrupted.
	 */
	public final Message receive() {
		return this.receive(-1);
	}

	/**
	 * Receive the first available message from this channel. If the channel
	 * contains no messages, this method will block until the allotted timeout
	 * elapses. If the specified timeout is 0, the method will return
	 * immediately. If less than zero, it will block indefinitely (see
	 * {@link #receive()}).
	 * 
	 * @param timeout the timeout in milliseconds
	 * 
	 * @return the first available message or <code>null</code> if no message
	 * is available within the allotted time or the receiving thread is
	 * interrupted.
	 */
	public final Message receive(long timeout) {
		if (!this.interceptors.preReceive(this)) {
			return null;
		}
		Message message = this.doReceive(timeout);
		this.interceptors.postReceive(message, this);
		return message;
	}


	/**
	 * Subclasses must implement this method. A non-negative timeout indicates
	 * how long to wait if the channel is at capacity (if the value is 0, it
	 * must return immediately with or without success). A negative timeout
	 * value indicates that the method should block until either the message is
	 * accepted or the blocking thread is interrupted.
	 */
	protected abstract boolean doSend(Message message, long timeout);

	/**
	 * Subclasses must implement this method. A non-negative timeout indicates
	 * how long to wait if the channel is empty (if the value is 0, it must
	 * return immediately with or without success). A negative timeout value
	 * indicates that the method should block until either a message is
	 * available or the blocking thread is interrupted.
	 */
	protected abstract Message doReceive(long timeout);


	/**
	 * A convenience wrapper class for the list of ChannelInterceptors.
	 */
	private static class ChannelInterceptorList {

		private final List<ChannelInterceptor> interceptors = new CopyOnWriteArrayList<ChannelInterceptor>();


		public boolean set(List<ChannelInterceptor> interceptors) {
			synchronized (this.interceptors) {
				this.interceptors.clear();
				return this.interceptors.addAll(interceptors);
			}
		}

		public boolean add(ChannelInterceptor interceptor) {
			return this.interceptors.add(interceptor);
		}

		public boolean preSend(Message message, MessageChannel channel) {
			for (ChannelInterceptor interceptor : interceptors) {
				if (!interceptor.preSend(message, channel)) {
					return false;
				}
			}
			return true;
		}

		public void postSend(Message message, MessageChannel channel, boolean sent) {
			for (ChannelInterceptor interceptor : interceptors) {
				interceptor.postSend(message, channel, sent);
			}
		}

		public boolean preReceive(MessageChannel channel) {
			for (ChannelInterceptor interceptor : interceptors) {
				if (!interceptor.preReceive(channel)) {
					return false;
				}
			}
			return true;
		}

		public void postReceive(Message message, MessageChannel channel) {
			for (ChannelInterceptor interceptor : interceptors) {
				interceptor.postReceive(message, channel);
			}
		}
	}

}
