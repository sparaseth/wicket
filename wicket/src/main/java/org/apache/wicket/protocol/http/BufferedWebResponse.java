/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.protocol.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.Cookie;

import org.apache.wicket.Response;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.util.lang.Checks;

/**
 * Subclass of {@link WebResponse} that buffers the actions and performs those on another response.
 * 
 * @see #writeTo(WebResponse)
 * 
 * @author Matej Knopp
 */
public abstract class BufferedWebResponse extends WebResponse
{

	private static abstract class Action
	{
		protected abstract void invoke(WebResponse response);
	};

	private static class WriteCharSequenceAction extends Action
	{
		private final StringBuilder builder = new StringBuilder(4096);

		public WriteCharSequenceAction()
		{

		}

		public void append(CharSequence sequence)
		{
			builder.append(sequence);
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.write(builder);
		}
	};

	private static class WriteDataAction extends Action
	{
		private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

		public WriteDataAction()
		{

		}

		public void append(byte data[])
		{
			try
			{
				stream.write(data);
			}
			catch (IOException e)
			{
				throw new WicketRuntimeException(e);
			}
		}

		@Override
		protected void invoke(WebResponse response)
		{
			writeStream(response, stream);
		}
	}

	private static class CloseAction extends Action
	{
		@Override
		protected void invoke(WebResponse response)
		{
			response.close();
		}
	};

	private static class AddCookieAction extends Action
	{
		private final Cookie cookie;

		public AddCookieAction(Cookie cookie)
		{
			this.cookie = cookie;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.addCookie(cookie);
		}
	};

	private static class ClearCookieAction extends Action
	{
		private final Cookie cookie;

		public ClearCookieAction(Cookie cookie)
		{
			this.cookie = cookie;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.clearCookie(cookie);
		}
	};

	private static class SetHeaderAction extends Action
	{
		private final String name;
		private final String value;

		public SetHeaderAction(String name, String value)
		{
			this.name = name;
			this.value = value;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.setHeader(name, value);
		}
	}

	private static class SetDateHeaderAction extends Action
	{
		private final String name;
		private final long value;

		public SetDateHeaderAction(String name, long value)
		{
			this.name = name;
			this.value = value;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.setDateHeader(name, value);
		}
	}

	private static class SetContentLengthAction extends Action
	{
		private final long contentLength;

		public SetContentLengthAction(long contentLength)
		{
			this.contentLength = contentLength;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.setContentLength(contentLength);
		}
	};

	private static class SetContentTypeAction extends Action
	{
		private final String contentType;

		public SetContentTypeAction(String contentType)
		{
			this.contentType = contentType;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.setContentType(contentType);
		}
	};

	private static class SetStatusAction extends Action
	{
		private final int sc;

		public SetStatusAction(int sc)
		{
			this.sc = sc;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.setStatus(sc);
		}
	};

	private static class SendErrorAction extends Action
	{
		private final int sc;
		private final String msg;

		public SendErrorAction(int sc, String msg)
		{
			this.sc = sc;
			this.msg = msg;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.sendError(sc, msg);
		}
	};

	private static class SendRedirectAction extends Action
	{
		private final String url;

		public SendRedirectAction(String url)
		{
			this.url = url;
		}

		@Override
		protected void invoke(WebResponse response)
		{
			response.sendRedirect(url);
		}
	};

	private static class FlushAction extends Action
	{
		@Override
		protected void invoke(WebResponse response)
		{
			response.flush();
		}
	};

	/**
	 * Construct.
	 */
	public BufferedWebResponse()
	{
	}

	private final List<Action> actions = new ArrayList<Action>();
	private WriteCharSequenceAction charSequenceAction;
	private WriteDataAction dataAction;

	@Override
	public void addCookie(Cookie cookie)
	{
		actions.add(new AddCookieAction(cookie));
	}

	@Override
	public void clearCookie(Cookie cookie)
	{
		actions.add(new ClearCookieAction(cookie));
	}

	@Override
	public void setContentLength(long length)
	{
		actions.add(new SetContentLengthAction(length));
	}

	@Override
	public void setContentType(String mimeType)
	{
		actions.add(new SetContentTypeAction(mimeType));
	}

	@Override
	public void setDateHeader(String name, long date)
	{
		actions.add(new SetDateHeaderAction(name, date));
	}

	@Override
	public void setHeader(String name, String value)
	{
		actions.add(new SetHeaderAction(name, value));
	}

	@Override
	public void write(CharSequence sequence)
	{
		if (dataAction != null)
		{
			throw new IllegalStateException(
				"Can't call write(CharSequence) after write(byte[]) has been called.");
		}

		if (charSequenceAction == null)
		{
			charSequenceAction = new WriteCharSequenceAction();
			actions.add(charSequenceAction);
		}
		charSequenceAction.append(sequence);
	}

	@Override
	public void write(byte[] array)
	{
		if (charSequenceAction != null)
		{
			throw new IllegalStateException(
				"Can't call write(byte[]) after write(CharSequence) has been called.");
		}
		if (dataAction == null)
		{
			dataAction = new WriteDataAction();
			actions.add(dataAction);
		}
		dataAction.append(array);
	}

	@Override
	public void sendRedirect(String url)
	{
		actions.add(new SendRedirectAction(url));
	}

	@Override
	public void setStatus(int sc)
	{
		actions.add(new SetStatusAction(sc));
	}

	@Override
	public void sendError(int sc, String msg)
	{
		actions.add(new SendErrorAction(sc, msg));
	}

	/**
	 * Writes the content of the buffer to the specified response. Also sets the properties and and
	 * headers.
	 * 
	 * @param response
	 */
	public void writeTo(final WebResponse response)
	{
		Checks.argumentNotNull(response, "response");

		for (Action action : actions)
		{
			action.invoke(response);
		}
	}

	@Override
	public boolean isRedirect()
	{
		for (Action action : actions)
		{
			if (action instanceof SendRedirectAction)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void flush()
	{
		actions.add(new FlushAction());
	}

	private static final void writeStream(final Response response, ByteArrayOutputStream stream)
	{
		final boolean copied[] = { false };
		try
		{
			// try to avoid copying the array
			stream.writeTo(new OutputStream()
			{
				@Override
				public void write(int b) throws IOException
				{

				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException
				{
					if (off == 0 && len == b.length)
					{
						response.write(b);
						copied[0] = true;
					}
				}
			});
		}
		catch (IOException e1)
		{
			throw new WicketRuntimeException(e1);
		}
		if (copied[0] == false)
		{
			response.write(stream.toByteArray());
		}
	}
}
