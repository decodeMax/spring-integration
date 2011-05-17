/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.integration.core;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.UUID;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.MessageHeaders;
import org.springframework.integration.MessageHeaders.IdGenerator;
import org.springframework.integration.message.GenericMessage;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;

/**
 * @author Oleg Zhurakousky
 */
public class MessageIdGenerationTests {

	@Test
	public void testCustomIdGenerationWithParentRegistrar(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context-withGenerator.xml", this.getClass());
		ClassPathXmlApplicationContext child = new ClassPathXmlApplicationContext(new String[]{"MessageIdGenerationTests-context.xml"}, this.getClass(), parent);

		IdGenerator idGenerator = child.getBean("idGenerator", IdGenerator.class);
		MessageChannel inputChannel = child.getBean("input", MessageChannel.class);
		inputChannel.send(new GenericMessage<Integer>(0));
		verify(idGenerator, times(4)).generateId();
		reset(idGenerator);
		child.close();
		new GenericMessage<Integer>(0);
		verify(idGenerator, times(1)).generateId();
		parent.close();
	}
	
	@Test
	public void testCustomIdGenerationWithParentChileIndependentCreation(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context-withGenerator.xml", this.getClass());
		GenericXmlApplicationContext child = new GenericXmlApplicationContext();
		child.load("classpath:/org/springframework/integration/core/MessageIdGenerationTests-context.xml");
		child.setParent(parent);
		child.refresh();

		IdGenerator idGenerator = child.getBean("idGenerator", IdGenerator.class);
		MessageChannel inputChannel = child.getBean("input", MessageChannel.class);
		inputChannel.send(new GenericMessage<Integer>(0));
		verify(idGenerator, times(4)).generateId();
		reset(idGenerator);
		child.close();
		new GenericMessage<Integer>(0);
		verify(idGenerator, times(1)).generateId();
		parent.close();
	}
	
	@Test
	public void testCustomIdGenerationWithParentRegistrarClosed(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context-withGenerator.xml", this.getClass());
		ClassPathXmlApplicationContext child = new ClassPathXmlApplicationContext(new String[]{"MessageIdGenerationTests-context.xml"}, this.getClass(), parent);

		IdGenerator idGenerator = child.getBean("idGenerator", IdGenerator.class);
		MessageChannel inputChannel = child.getBean("input", MessageChannel.class);
		inputChannel.send(new GenericMessage<Integer>(0));
		verify(idGenerator, times(4)).generateId();
		reset(idGenerator);
		parent.close();
		new GenericMessage<Integer>(0);
		verify(idGenerator, times(0)).generateId();
		child.close();
	}
	
	@Test
	public void testCustomIdGenerationWithChildRegistrar(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context.xml", this.getClass());
		ClassPathXmlApplicationContext child = new ClassPathXmlApplicationContext(new String[]{"MessageIdGenerationTests-context-withGenerator.xml"}, this.getClass(), parent);

		IdGenerator idGenerator = child.getBean("idGenerator", IdGenerator.class);
		MessageChannel inputChannel = child.getBean("input", MessageChannel.class);
		inputChannel.send(new GenericMessage<Integer>(0));
		verify(idGenerator, times(4)).generateId();
		reset(idGenerator);
		parent.close();
		new GenericMessage<Integer>(0);
		verify(idGenerator, times(1)).generateId();
		child.close();
	}
	
	@Test
	public void testCustomIdGenerationWithChildRegistrarClosed(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context.xml", this.getClass());
		ClassPathXmlApplicationContext child = new ClassPathXmlApplicationContext(new String[]{"MessageIdGenerationTests-context-withGenerator.xml"}, this.getClass(), parent);

		IdGenerator idGenerator = child.getBean("idGenerator", IdGenerator.class);
		MessageChannel inputChannel = child.getBean("input", MessageChannel.class);
		inputChannel.send(new GenericMessage<Integer>(0));
		verify(idGenerator, times(4)).generateId();
		reset(idGenerator);
		child.close();
		new GenericMessage<Integer>(0);
		verify(idGenerator, times(0)).generateId();
		parent.close();
	}

	// similar to the last test, but should not fail because child AC is closed before second child AC is started
	@Test
	public void testCustomIdGenerationWithParentChildIndependentCreationChildrenRegistrarsOneAtTheTime(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context.xml", this.getClass());
		
		GenericXmlApplicationContext childA = new GenericXmlApplicationContext();
		childA.load("classpath:/org/springframework/integration/core/MessageIdGenerationTests-context-withGenerator.xml");
		childA.setParent(parent);
		childA.refresh();
		
		childA.close();
		
		GenericXmlApplicationContext childB = new GenericXmlApplicationContext();
		childB.load("classpath:/org/springframework/integration/core/MessageIdGenerationTests-context-withGenerator.xml");
		childB.setParent(parent);
		childB.refresh();
		
		parent.close();
		childB.close();
	}

	// should fail because both parent and child define IdGenerator instances
	@Test(expected=BeanDefinitionStoreException.class)
	public void testCustomIdGenerationWithParentChildIndependentCreation(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context-withGenerator.xml", this.getClass());
		
		GenericXmlApplicationContext child = new GenericXmlApplicationContext();
		try {
			child.load("classpath:/org/springframework/integration/core/MessageIdGenerationTests-context-withGenerator.xml");
			child.setParent(parent);
			child.refresh();
		}
		finally {
			child.close();
			parent.close();
		}
	}

	// should fail because second child attempts to register another instance of IdGenerator
	@Test(expected=BeanDefinitionStoreException.class)
	public void testCustomIdGenerationWithParentChildIndependentCreationChildrenRegistrars(){
		ClassPathXmlApplicationContext parent = new ClassPathXmlApplicationContext("MessageIdGenerationTests-context.xml", this.getClass());
		
		GenericXmlApplicationContext childA = new GenericXmlApplicationContext();
		GenericXmlApplicationContext childB = new GenericXmlApplicationContext();

		try {
			childA.load("classpath:/org/springframework/integration/core/MessageIdGenerationTests-context-withGenerator.xml");
			childA.setParent(parent);
			childA.refresh();
	
			childB.load("classpath:/org/springframework/integration/core/MessageIdGenerationTests-context-withGenerator.xml");
			childB.setParent(parent);
			childB.refresh();
		}
		finally {
			childA.close();
			childB.close();
			parent.close();
		}
	}
	
	@Test
	@Ignore
	public void performanceTest(){
		int times = 1000000;
		StopWatch watch = new StopWatch();
		watch.start();
		for (int i = 0; i < times; i++) {
			new GenericMessage<Integer>(0);
		}
		watch.stop();
		double defaultGeneratorElapsedTime = watch.getTotalTimeSeconds();

		Field idGeneratorField = ReflectionUtils.findField(MessageHeaders.class, "idGenerator");
		ReflectionUtils.makeAccessible(idGeneratorField);
		ReflectionUtils.setField(idGeneratorField, null, new IdGenerator() {	
			public UUID generateId() {
				return TimeBasedUUIDGenerator.generateId();
			}
		});
		watch = new StopWatch();
		watch.start();
		for (int i = 0; i < times; i++) {
			new GenericMessage<Integer>(0);
		}
		watch.stop();
		double timebasedGeneratorElapsedTime = watch.getTotalTimeSeconds();
		
		System.out.println("Generated " + times + " messages using default UUID generator " +
				"in " + defaultGeneratorElapsedTime + " seconds");
		System.out.println("Generated " + times + " messages using Timebased UUID generator " +
				"in " + timebasedGeneratorElapsedTime + " seconds");
		
		System.out.println("Time-based ID generator is " + defaultGeneratorElapsedTime/timebasedGeneratorElapsedTime + " times faster");
	
	}
	
	@After
	public void cleanup(){
		Field idGenField = ReflectionUtils.findField(MessageHeaders.class, "idGenerator");
		ReflectionUtils.makeAccessible(idGenField);
		ReflectionUtils.setField(idGenField, null, null);
	}

	
	public static class SampleIdGenerator implements IdGenerator {
		
		public UUID generateId() {
			return UUID.nameUUIDFromBytes(((System.currentTimeMillis() - System.nanoTime()) + "").getBytes());
		}
		
	}}