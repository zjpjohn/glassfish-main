

/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * Portions Copyright Apache Software Foundation.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package javax.servlet.http;

import java.io.IOException;

import javax.servlet.ServletResponseWrapper;

/**
 * 
 * Provides a convenient implementation of the HttpServletResponse interface that
 * can be subclassed by developers wishing to adapt the response from a Servlet.
 * This class implements the Wrapper or Decorator pattern. Methods default to
 * calling through to the wrapped response object.
 * 
 * @author 	Various
 * @since	v 2.3
 *
 * @see 	javax.servlet.http.HttpServletResponse
 *
 */

public class HttpServletResponseWrapper extends ServletResponseWrapper implements HttpServletResponse {


    /** 
    * Constructs a response adaptor wrapping the given response.
    * @throws java.lang.IllegalArgumentException if the response is null
    */
    public HttpServletResponseWrapper(HttpServletResponse response) {
	    super(response);
    }
    
    private HttpServletResponse _getHttpServletResponse() {
	return (HttpServletResponse) super.getResponse();
    }
    
    /**
     * The default behavior of this method is to call addCookie(Cookie cookie)
     * on the wrapped response object.
     */
    public void addCookie(Cookie cookie) {
	this._getHttpServletResponse().addCookie(cookie);
    }

    /**
     * The default behavior of this method is to call containsHeader(String name)
     * on the wrapped response object.
     */

 
    public boolean containsHeader(String name) {
	return this._getHttpServletResponse().containsHeader(name);
    }
    
    /**
     * The default behavior of this method is to call encodeURL(String url)
     * on the wrapped response object.
     */
    public String encodeURL(String url) {
	return this._getHttpServletResponse().encodeURL(url);
    }

    /**
     * The default behavior of this method is to return encodeRedirectURL(String url)
     * on the wrapped response object.
     */
    public String encodeRedirectURL(String url) {
	return this._getHttpServletResponse().encodeRedirectURL(url);
    }

    /**
     * The default behavior of this method is to call encodeUrl(String url)
     * on the wrapped response object.
     */
    public String encodeUrl(String url) {
	return this._getHttpServletResponse().encodeUrl(url);
    }
    
    /**
     * The default behavior of this method is to return encodeRedirectUrl(String url)
     * on the wrapped response object.
     */
    public String encodeRedirectUrl(String url) {
	return this._getHttpServletResponse().encodeRedirectUrl(url);
    }
    
    /**
     * The default behavior of this method is to call sendError(int sc, String msg)
     * on the wrapped response object.
     */
    public void sendError(int sc, String msg) throws IOException {
	this._getHttpServletResponse().sendError(sc, msg);
    }

    /**
     * The default behavior of this method is to call sendError(int sc)
     * on the wrapped response object.
     */


    public void sendError(int sc) throws IOException {
	this._getHttpServletResponse().sendError(sc);
    }

    /**
     * The default behavior of this method is to return sendRedirect(String location)
     * on the wrapped response object.
     */
    public void sendRedirect(String location) throws IOException {
	this._getHttpServletResponse().sendRedirect(location);
    }
    
    /**
     * The default behavior of this method is to call setDateHeader(String name, long date)
     * on the wrapped response object.
     */
    public void setDateHeader(String name, long date) {
	this._getHttpServletResponse().setDateHeader(name, date);
    }
    
    /**
     * The default behavior of this method is to call addDateHeader(String name, long date)
     * on the wrapped response object.
     */
   public void addDateHeader(String name, long date) {
	this._getHttpServletResponse().addDateHeader(name, date);
    }
    
    /**
     * The default behavior of this method is to return setHeader(String name, String value)
     * on the wrapped response object.
     */
    public void setHeader(String name, String value) {
	this._getHttpServletResponse().setHeader(name, value);
    }
    
    /**
     * The default behavior of this method is to return addHeader(String name, String value)
     * on the wrapped response object.
     */
     public void addHeader(String name, String value) {
	this._getHttpServletResponse().addHeader(name, value);
    }
    
    /**
     * The default behavior of this method is to call setIntHeader(String name, int value)
     * on the wrapped response object.
     */
    public void setIntHeader(String name, int value) {
	this._getHttpServletResponse().setIntHeader(name, value);
    }
    
    /**
     * The default behavior of this method is to call addIntHeader(String name, int value)
     * on the wrapped response object.
     */
    public void addIntHeader(String name, int value) {
	this._getHttpServletResponse().addIntHeader(name, value);
    }

    /**
     * The default behavior of this method is to call setStatus(int sc)
     * on the wrapped response object.
     */


    public void setStatus(int sc) {
	this._getHttpServletResponse().setStatus(sc);
    }
    
    /**
     * The default behavior of this method is to call setStatus(int sc, String sm)
     * on the wrapped response object.
     */
     public void setStatus(int sc, String sm) {
	this._getHttpServletResponse().setStatus(sc, sm);
    }

   
}
