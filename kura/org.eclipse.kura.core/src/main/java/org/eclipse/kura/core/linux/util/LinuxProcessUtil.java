/**
 * Copyright (c) 2011, 2014 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 */
package org.eclipse.kura.core.linux.util;

/* 
 * Copyright (c) 2013 Eurotech Inc. All rights reserved.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import org.eclipse.kura.core.util.ProcessUtil;
import org.eclipse.kura.core.linux.util.ProcessStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinuxProcessUtil {

	private static final Logger s_logger = LoggerFactory
			.getLogger(LinuxProcessUtil.class);

	public static int start(String command, boolean wait, boolean background)
			throws Exception {
		Process proc = null;
		try {
			s_logger.info("executing: " + command);
			proc = ProcessUtil.exec(command);
			if (wait) {
				try {
					proc.waitFor();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				s_logger.info(command + " returned with exit value:"
						+ proc.exitValue());
				if (proc.exitValue() > 0) {
					String stdout = getInputStreamAsString(proc
							.getInputStream());
					String stderr = getInputStreamAsString(proc
							.getErrorStream());
					s_logger.debug("stdout: " + stdout);
					s_logger.debug("stderr: " + stderr);
				}
				return proc.exitValue();
			} else {
				return 0;
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if (!background) {
				ProcessUtil.destroy(proc);
			}
		}
	}

	public static int start(String command) throws Exception {
		return LinuxProcessUtil.start(command, true, false);
	}

	public static int start(String command, boolean wait) throws Exception {
		return start(command, wait, false);
	}

	public static int start(String[] command, boolean wait) throws Exception {
		StringBuilder cmdBuilder = new StringBuilder();
		for (String cmd : command) {
			cmdBuilder.append(cmd).append(' ');
		}
		return start(cmdBuilder.toString(), wait);
	}

	public static int startBackground(String command, boolean wait)
			throws Exception {
		return start(command, wait, true);
	}

	public static ProcessStats startWithStats(String command) throws Exception {
		Process proc = null;
		try {
			s_logger.info("executing: " + command);
			proc = ProcessUtil.exec(command);

			try {
				int exitVal = proc.waitFor();
				s_logger.info(command + " returned with exit value:" + exitVal);
			} catch (InterruptedException e) {
				s_logger.error("error executing " + command + " command" + e);
				// e.printStackTrace();
			}

			ProcessStats stats = new ProcessStats(proc);
			// s_logger.info(command + " returned with exit value:" +
			// proc.exitValue());
			return stats;
		} catch (Exception e) {
			throw e;
		}
	}

	public static ProcessStats startWithStats(String[] command)
			throws Exception {
		Process proc = null;
		try {
			StringBuilder cmdBuilder = new StringBuilder();
			for (String cmd : command) {
				cmdBuilder.append(cmd).append(' ');
			}
			s_logger.debug("executing: " + cmdBuilder);
			proc = ProcessUtil.exec(command);

			try {
				int exitVal = proc.waitFor();
				s_logger.debug(cmdBuilder + " returned with exit value:"
						+ exitVal);
			} catch (InterruptedException e) {
				s_logger.error("error executing " + command + " command" + e);
				// e.printStackTrace();
			}

			ProcessStats stats = new ProcessStats(proc);
			// s_logger.debug(cmdBuilder + " returned with exit value:" +
			// proc.exitValue());
			return stats;
		} catch (Exception e) {
			throw e;
		}
	}

	public static int getPid(String command) throws Exception {
		StringTokenizer st = null;
		String line = null;
		String pid = null;
		Process proc = null;
		BufferedReader br = null;
		try {

			if (command != null && !command.isEmpty()) {
				s_logger.trace("searching process list for " + command);
				proc = ProcessUtil.exec("ps -ax");

				// get the output
				br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
				while ((line = br.readLine()) != null) {
					st = new StringTokenizer(line);
					pid = st.nextToken();
					st.nextElement();
					st.nextElement();
					st.nextElement();

					// get the remainder of the line showing the command that
					// was issued
					line = line.substring(line.indexOf(st.nextToken()));

					// see if the line has our command
					if (line.indexOf(command) >= 0) {
						s_logger.trace("found pid " + pid + " for command: "
								+ command);
						return Integer.parseInt(pid);
					}
				}
			}

			// return failure
			return -1;
		} catch (Exception e) {
			throw e;
		} finally {
			if(br != null) br.close();
			ProcessUtil.destroy(proc);
		}
	}

	public static int getKuraPid() throws Exception {

		int pid = -1;
		File kuraPidFile = new File("/var/run/kura.pid");
		if (kuraPidFile.exists()) {
			BufferedReader br;
			br = new BufferedReader(new FileReader(kuraPidFile));
			pid = Integer.parseInt(br.readLine());
			br.close();
		}
		return pid;
	}

	public static boolean stop(int pid) {
		return stop(pid, false);
	}

	public static boolean kill(int pid) {
		return stop(pid, true);
	}

	private static boolean stop(int pid, boolean kill) {
		try {
			StringBuffer cmd = new StringBuffer();
			cmd.append("kill ");
			if (kill) {
				cmd.append("-9 ");
			}
			cmd.append(pid);

			if (kill) {
				s_logger.info("attempting to kill -9 pid " + pid);
			} else {
				s_logger.info("attempting to kill pid " + pid);
			}

			if (start(cmd.toString()) == 0) {
				s_logger.info("successfully killed pid " + pid);
				return true;
			} else {
				s_logger.warn("failed to kill pid " + pid);
				return false;
			}
		} catch (Exception e) {
			s_logger.warn("failed to kill pid " + pid);
			return false;
		}
	}

	public static boolean killAll(String command) {
		try {
			s_logger.info("attempting to kill process " + command);
			if (start("killall " + command) == 0) {
				s_logger.info("successfully killed process " + command);
				return true;
			} else {
				s_logger.warn("failed to kill process " + command);
				return false;
			}
		} catch (Exception e) {
			s_logger.warn("failed to kill process " + command);
			return false;
		}
	}

	public static String getInputStreamAsString(InputStream stream)
			throws IOException {
		StringBuffer sb = new StringBuffer();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(stream));
			char[] cbuf = new char[1024];
			int len;
			while ((len = br.read(cbuf)) > 0) {
				sb.append(cbuf, 0, len);
			}
		} 
		finally {
			if(br != null) br.close();		
		}
		return sb.toString();
	}
}
