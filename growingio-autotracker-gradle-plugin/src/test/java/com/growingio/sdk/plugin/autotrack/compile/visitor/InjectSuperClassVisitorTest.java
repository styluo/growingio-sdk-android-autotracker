/*
 * Copyright (C) 2020 Beijing Yishu Technology Co., Ltd.
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

package com.growingio.sdk.plugin.autotrack.compile.visitor;

import com.google.common.truth.Truth;
import com.growingio.sdk.plugin.autotrack.ByteCodeClassLoader;
import com.growingio.sdk.plugin.autotrack.ClassUtils;
import com.growingio.sdk.plugin.autotrack.compile.Context;
import com.growingio.sdk.plugin.autotrack.compile.SystemLog;
import com.growingio.sdk.plugin.autotrack.hook.HookClassesConfig;
import com.growingio.sdk.plugin.autotrack.hook.InjectMethod;
import com.growingio.sdk.plugin.autotrack.hook.TargetClass;
import com.growingio.sdk.plugin.autotrack.hook.TargetMethod;
import com.growingio.sdk.plugin.autotrack.tmp.Callback;
import com.growingio.sdk.plugin.autotrack.tmp.SubExample;
import com.growingio.sdk.plugin.autotrack.tmp.SubOverrideExample;
import com.growingio.sdk.plugin.autotrack.tmp.SuperExample;
import com.growingio.sdk.plugin.autotrack.tmp.inject.InjectAgent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HookClassesConfig.class})
public class InjectSuperClassVisitorTest {
    @Before
    public void setUp() {
        PowerMockito.mockStatic(HookClassesConfig.class);
    }

    private void mockSuperHookClasses(boolean isAfter) {
        Map<String, TargetClass> targetClassMap = new HashMap<>();
        String className = ClassUtils.getClassName(SuperExample.class);
        TargetClass targetClass = new TargetClass(className);
        targetClassMap.put(className, targetClass);
        TargetMethod targetMethod = new TargetMethod("onExecute", "()V");
        targetClass.addTargetMethod(targetMethod);
        targetMethod.addInjectMethod(new InjectMethod(ClassUtils.getClassName(InjectAgent.class), "onExecute", "(L" + className + ";)V", isAfter));
        PowerMockito.when(HookClassesConfig.getSuperHookClasses()).thenReturn(targetClassMap);
    }

    public SuperExample getSpySubExample(Class<?> clazz) throws IOException, IllegalAccessException, InstantiationException {
        InputStream resourceAsStream = ClassUtils.classToInputStream(clazz);
        ClassReader cr = new ClassReader(resourceAsStream);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        Context context = new Context(new SystemLog(), getClass().getClassLoader());
        cr.accept(new InjectSuperClassVisitor(cw, context), ClassReader.SKIP_FRAMES | ClassReader.EXPAND_FRAMES);
        Class<?> aClass = new ByteCodeClassLoader(getClass().getClassLoader()).defineClass(clazz.getName(), cw.toByteArray());
        return (SuperExample) PowerMockito.spy(aClass.newInstance());
    }

    @Test
    public void injectSuperBefore() throws IOException, IllegalAccessException, InstantiationException, NoSuchFieldException {
        mockSuperHookClasses(false);

        final SuperExample spySubExample = getSpySubExample(SubExample.class);
        final SuperExample[] callbackResult = new SuperExample[1];
        InjectAgent.setCallback(new Callback() {
            @Override
            public void onCallback(SuperExample example) {
                Truth.assertThat(spySubExample == example).isTrue();
                Truth.assertThat(example.isExecuted()).isFalse();
                callbackResult[0] = example;
                example.preOriginExecute();
            }
        });
        spySubExample.onExecute();
        Field isExecuted = SuperExample.class.getDeclaredField("mIsExecuted");
        isExecuted.setAccessible(true);
        Truth.assertThat(callbackResult[0].isExecuted()).isTrue();

        Mockito.verify(spySubExample, Mockito.times(1)).preOriginExecute();
    }

    @Test
    public void injectSuperBefore_override() throws IOException, IllegalAccessException, InstantiationException, NoSuchFieldException {
        mockSuperHookClasses(false);

        final SuperExample spySubExample = getSpySubExample(SubOverrideExample.class);
        final SuperExample[] callbackResult = new SuperExample[1];
        InjectAgent.setCallback(new Callback() {
            @Override
            public void onCallback(SuperExample example) {
                Truth.assertThat(spySubExample == example).isTrue();
                Truth.assertThat(example.isExecuted()).isFalse();
                callbackResult[0] = example;
                example.preOriginExecute();
            }
        });
        spySubExample.onExecute();
        Field isExecuted = SuperExample.class.getDeclaredField("mIsExecuted");
        isExecuted.setAccessible(true);
        Truth.assertThat(callbackResult[0].isExecuted()).isTrue();

        InOrder inOrder = Mockito.inOrder(spySubExample);
        inOrder.verify(spySubExample).onExecute();
        inOrder.verify(spySubExample).preOriginExecute();
        inOrder.verify(spySubExample).originExecute();
    }

    @Test
    public void injectSuperAfter_override() throws IOException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, NoSuchFieldException {
        mockSuperHookClasses(true);

        final SuperExample spySubExample = getSpySubExample(SubOverrideExample.class);
        final SuperExample[] callbackResult = new SuperExample[1];
        InjectAgent.setCallback(new Callback() {
            @Override
            public void onCallback(SuperExample example) {
                Truth.assertThat(spySubExample == example).isTrue();
                Truth.assertThat(example.isExecuted()).isTrue();
                callbackResult[0] = example;
                example.postOriginExecute();
            }
        });
        spySubExample.onExecute();
        Field isExecuted = SuperExample.class.getDeclaredField("mIsExecuted");
        isExecuted.setAccessible(true);
        Truth.assertThat(callbackResult[0].isExecuted()).isTrue();

        InOrder inOrder = Mockito.inOrder(spySubExample);
        inOrder.verify(spySubExample).onExecute();
        inOrder.verify(spySubExample).originExecute();
        inOrder.verify(spySubExample).postOriginExecute();
    }
}