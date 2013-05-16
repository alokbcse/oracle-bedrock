/*
 * File: ContainerScope.java
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * The contents of this file are subject to the terms and conditions of 
 * the Common Development and Distribution License 1.0 (the "License").
 *
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the License by consulting the LICENSE.txt file
 * distributed with this file, or by consulting https://oss.oracle.com/licenses/CDDL
 *
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file LICENSE.txt.
 *
 * MODIFICATIONS:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 */

package com.oracle.tools.runtime.java.container;

import com.oracle.tools.runtime.network.AvailablePortIterator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

import java.util.Properties;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Scope} that provides isolation of Container-based application resources.
 * <p>
 * Copyright (c) 2013. All Rights Reserved. Oracle Corporation.<br>
 * Oracle is a registered trademark of Oracle Corporation and/or its affiliates.
 *
 * @author Brian Oliver
 */
public class ContainerScope extends Scope
{
    /**
     * The {@link java.io.PipedOutputStream} to which Standard Output will be written when
     * an application uses this {@link ContainerScope}.
     * <p>
     * NOTE: This will be <code>null</code> when the Scope represents the
     * underlying Java Virtual Machine resources.
     */
    private PipedOutputStream m_stdoutPipedOutputStream;

    /**
     * The {@link java.io.PipedInputStream} from which Standard Output written by an
     * application using this {@link ContainerScope}, may be read.
     * <p>
     * NOTE: This will be <code>null</code> when the Scope represents the
     * underlying Java Virtual Machine resources.
     */
    private PipedInputStream m_stdoutPipedInputStream;

    /**
     * Is the Standard Error Stream redirected to the Standard Output?
     */
    private boolean m_isErrorStreamRedirected;

    /**
     * The {@link java.io.PipedOutputStream} to which Standard Error will be written when
     * an application uses this {@link ContainerScope}.
     * <p>
     * NOTE: This will be <code>null</code> when the Scope represents the
     * underlying Java Virtual Machine resources.
     */
    private PipedOutputStream m_stderrPipedOutputStream;

    /**
     * The {@link java.io.PipedInputStream} from which Standard Error written by an
     * application using this {@link ContainerScope}, may be read.
     * <p>
     * NOTE: This will be <code>null</code> when the Scope represents the
     * underlying Java Virtual Machine resources.
     */
    private PipedInputStream m_stderrPipedInputStream;

    /**
     * The {@link java.io.PipedOutputStream} to which Standard Input to an application
     * using this {@link ContainerScope}, may be written.
     * <p>
     * NOTE: This will be <code>null</code> when the Scope represents the
     * underlying Java Virtual Machine resources.
     */
    private PipedOutputStream m_stdinPipedOutputStream;

    /**
     * The {@link java.io.PipedInputStream} from which Standard Input for an
     * application using this {@link ContainerScope}, may be read.
     * <p>
     * NOTE: This will be <code>null</code> when the Scope represents the
     * underlying Java Virtual Machine resources.
     */
    private PipedInputStream m_stdinPipedInputStream;

    /**
     * The {@link ContainerMBeanServerBuilder} to be used when an application
     * is in this {@link ContainerScope}.
     */
    private ContainerMBeanServerBuilder m_mBeanServerBuilder;


    /**
     * Constructs a {@link ContainerScope} using appropriate defaults for
     * the underlying resources.
     *
     * @param name  the name of the scope
     */
    public ContainerScope(String name)
    {
        this(name, null, Container.getAvailablePorts(), null, false, Container.PIPE_BUFFER_SIZE_BYTES);
    }


    /**
     * Constructs a {@link ContainerScope} using appropriate defaults for
     * the underlying resources.
     *
     * @param name        the name of the scope
     * @param properties  the System {@link Properties} for the scope
     */
    public ContainerScope(String     name,
                          Properties properties)
    {
        this(name, properties, Container.getAvailablePorts(), null, false, Container.PIPE_BUFFER_SIZE_BYTES);
    }


    /**
     * Constructs a {@link ContainerScope}.
     *
     * @param name                 the name of the scope
     * @param properties           the System {@link java.util.Properties} for the scope
     * @param availablePorts       the {@link AvailablePortIterator} for the scope
     * @param mBeanServerBuilder   the {@link ContainerMBeanServerBuilder}
     *                             (if null a default will be created)
     * @param redirectErrorStream  should the stderr stream be redirected to stdout
     * @param pipeBufferSizeBytes  the number of bytes to reserve for i/o buffers
     */
    public ContainerScope(String                      name,
                          Properties                  properties,
                          AvailablePortIterator       availablePorts,
                          ContainerMBeanServerBuilder mBeanServerBuilder,
                          boolean                     redirectErrorStream,
                          int                         pipeBufferSizeBytes)
    {
        super(name, new Properties(), availablePorts);

        // add/overrider the specified properties with in the scope
        if (properties != null)
        {
            m_properties.putAll(properties);
        }

        m_isErrorStreamRedirected = redirectErrorStream;

        try
        {
            m_stdoutPipedOutputStream = new PipedOutputStream();
            m_stdoutPipedInputStream  = new PipedInputStream(m_stdoutPipedOutputStream, pipeBufferSizeBytes);
            m_stdout                  = new PrintStream(m_stdoutPipedOutputStream);

            if (redirectErrorStream)
            {
                m_stderrPipedOutputStream = null;
                m_stderrPipedInputStream  = null;
                m_stderr                  = m_stdout;
            }
            else
            {
                m_stderrPipedOutputStream = new PipedOutputStream();
                m_stderrPipedInputStream  = new PipedInputStream(m_stderrPipedOutputStream, pipeBufferSizeBytes);
                m_stderr                  = new PrintStream(m_stderrPipedOutputStream);
            }

            m_stdinPipedOutputStream = new PipedOutputStream();
            m_stdinPipedInputStream  = new PipedInputStream(m_stdinPipedOutputStream, pipeBufferSizeBytes);
            m_stdin                  = m_stdinPipedInputStream;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not establish i/o pipes for the ContainerScope [" + getName() + "]", e);
        }

        m_mBeanServerBuilder = mBeanServerBuilder == null
                               ? new ContainerMBeanServerBuilder(m_availablePorts) : mBeanServerBuilder;
    }


    /**
     * Obtains the {@link java.io.InputStream} that can be used to read the contents
     * of the Standard Output that has been written to this {@link ContainerScope}.
     *
     * @return the Standard Output {@link java.io.InputStream}
     */
    public InputStream getStandardOutputInputStream()
    {
        return m_stdoutPipedInputStream;
    }


    /**
     * Obtains the {@link java.io.InputStream} that can be used to read the contents
     * of the Standard Error that has been written to this {@link ContainerScope}.
     *
     * @return the Standard Error {@link java.io.InputStream}
     */
    public InputStream getStandardErrorInputStream()
    {
        if (m_isErrorStreamRedirected)
        {
            throw new UnsupportedOperationException("The Standard Error Stream has been redirected to the Standard Output Stream");
        }
        else
        {
            return m_stderrPipedInputStream;
        }
    }


    /**
     * Obtains the {@link java.io.OutputStream} that can be used to write content
     * of the Standard Input that can been read in this {@link ContainerScope}.
     *
     * @return the Standard Error {@link java.io.InputStream}
     */
    public OutputStream getStandardInputOutputStream()
    {
        return m_stdinPipedOutputStream;
    }


    /**
     * Obtains the {@link javax.management.MBeanServerBuilder} for this {@link ContainerScope}.
     *
     * @return the {@link javax.management.MBeanServerBuilder}
     */
    public ContainerMBeanServerBuilder getMBeanServerBuilder()
    {
        return m_mBeanServerBuilder;
    }


    /**
     * Closes the {@link ContainerScope}, after which the resources can't be used.
     */
    public void close()
    {
        if (m_isClosed.compareAndSet(false, true))
        {
            try
            {
                m_stdoutPipedOutputStream.close();
            }
            catch (Exception e)
            {
                // SKIP: we ignore exceptions
            }

            try
            {
                m_stdoutPipedInputStream.close();
            }
            catch (Exception e)
            {
                // SKIP: we ignore exceptions
            }

            try
            {
                m_stdout.close();
            }
            catch (Exception e)
            {
                // SKIP: we ignore exceptions
            }

            if (!m_isErrorStreamRedirected)
            {
                try
                {
                    m_stderrPipedOutputStream.close();
                }
                catch (Exception e)
                {
                    // SKIP: we ignore exceptions
                }

                try
                {
                    m_stderrPipedInputStream.close();
                }
                catch (Exception e)
                {
                    // SKIP: we ignore exceptions
                }

                try
                {
                    m_stderr.close();
                }
                catch (Exception e)
                {
                    // SKIP: we ignore exceptions
                }
            }

            try
            {
                m_stdinPipedOutputStream.close();
            }
            catch (Exception e)
            {
                // SKIP: we ignore exceptions
            }

            try
            {
                m_stdinPipedInputStream.close();
            }
            catch (Exception e)
            {
                // SKIP: we ignore exceptions
            }

            try
            {
                m_stdin.close();
            }
            catch (Exception e)
            {
                // SKIP: we ignore exceptions
            }
        }
    }
}