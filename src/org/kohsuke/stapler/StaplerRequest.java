package org.kohsuke.stapler;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

/**
 * Defines additional parameters/operations made available by Stapler.
 *
 * @see Stapler#getCurrentRequest()
 * @author Kohsuke Kawaguchi
 */
public interface StaplerRequest extends HttpServletRequest {
    /**
     * Returns the additional URL portion that wasn't used by the stapler,
     * excluding the query string.
     *
     * <p>
     * For example, if the requested URL is "foo/bar/zot/abc?def=ghi" and
     * "foo/bar" portion matched <tt>bar.jsp</tt>, this method returns
     * "/zot/abc".
     *
     * <p>
     * If this method is invoked from getters or {@link StaplerProxy#getTarget()}
     * during the object traversal, this method returns the path portion
     * that is not yet processed.
     *
     * @return
     *      can be empty string, but never null.
     */
    String getRestOfPath();

    /**
     * Returns the {@link ServletContext} object given to the stapler
     * dispatcher servlet.
     */
    ServletContext getServletContext();

    /**
     * Gets the {@link RequestDispatcher} that represents a specific view
     * for the given object.
     *
     * This support both JSP and Jelly.
     *
     * @param viewName
     *      If this name is relative name like "foo.jsp" or "bar/zot.jelly",
     *      then the corresponding "side file" is searched by this name.
     *      <p>
     *      For Jelly, this also accepts absolute path name that starts
     *      with '/', such as "/foo/bar/zot.jelly". In this case,
     *      <tt>it.getClass().getClassLoader()</tt> is searched for this script. 
     *
     * @return null
     *      if neither JSP nor Jelly is not found by the given name.
     */
    RequestDispatcher getView(Object it,String viewName) throws IOException;

    /**
     * Gets the part of the request URL from protocol up to the context path.
     * So typically it's something like <tt>http://foobar:8080/something</tt>
     */
    String getRootPath();

    /**
     * Gets the referer header (like "http://foobar.com/zot") or null.
     *
     * This is just a convenience method.
     */
    String getReferer();

    /**
     * Returns a list of ancestor objects that lead to the "it" object.
     * The returned list contains {@link Ancestor} objects sorted in the
     * order from root to the "it" object.
     *
     * <p>
     * For example, if the URL was "foo/bar/zot" and the "it" object
     * was determined as <code>root.getFoo().getBar("zot")</code>,
     * then this list will contain the following 3 objects in this order:
     * <ol>
     *  <li>the root object
     *  <li>root.getFoo() object
     *  <li>root.getFoo().getBar("zot") object (the "it" object)
     * </ol>
     * <p>
     * 
     *
     * @return
     *      list of {@link Ancestor}s. Can be empty, but always non-null.
     */
    List<Ancestor> getAncestors();

    /**
     * Finds the nearest ancestor that has the object of the given type, or null if not found.
     */
    Ancestor findAncestor(Class type);

    /**
     * Finds the nearest ancestor whose {@link Ancestor#getObject()} matches the given object.
     */
    Ancestor findAncestor(Object o);

    /**
     * Short for {@code getParameter(name)!=nulll}
     */
    boolean hasParameter(String name);

    /**
     * Gets the {@link HttpServletRequest#getRequestURI() request URI}
     * of the original request, so that you can access the value even from
     * JSP.
     */
    String getOriginalRequestURI();

    /**
     * Checks "If-Modified-Since" header and returns false
     * if the resource needs to be served.
     *
     * <p>
     * This method can behave in three ways.
     *
     * <ol>
     *  <li>If <tt>timestampOfResource</tt> is 0 or negative,
     *      this method just returns false.
     *
     *  <li>If "If-Modified-Since" header is sent and if it's bigger than
     *      <tt>timestampOfResource</tt>, then this method sets
     *      {@link HttpServletResponse#SC_NOT_MODIFIED} as the response code
     *      and returns true.
     *
     *  <li>Otherwise, "Last-Modified" header is added with <tt>timestampOfResource</tt> value,
     *      and this method returns false.
     * </ol>
     *
     * <p>
     * This method sends out the "Expires" header to force browser
     * to re-validate all the time.
     *
     * @param timestampOfResource
     *      The time stamp of the resource.
     * @param rsp
     *      This object is updated accordingly to simplify processing.
     *
     * @return
     *      false to indicate that the caller has to serve the actual resource.
     *      true to indicate that the caller should just quit processing right there
     *      (and send back {@link HttpServletResponse#SC_NOT_MODIFIED}.
     */
    boolean checkIfModified(long timestampOfResource, StaplerResponse rsp);

    /**
     * @see #checkIfModified(long, StaplerResponse)
     */
    boolean checkIfModified(Date timestampOfResource, StaplerResponse rsp);

    /**
     * @see #checkIfModified(long, StaplerResponse)
     */
    boolean checkIfModified(Calendar timestampOfResource, StaplerResponse rsp);

    /**
     * @param expiration
     *      The number of milliseconds until the resource will "expire".
     *      Until it expires the browser will be allowed to cache it
     *      and serve it without checking back with the server.
     *      After it expires, the client will send conditional GET to
     *      check if the resource is actually modified or not.
     *      If 0, it will immediately expire.
     *
     * @see #checkIfModified(long, StaplerResponse)
     */
    boolean checkIfModified(long timestampOfResource, StaplerResponse rsp, long expiration);

    /**
     * Binds form parameters to a bean by using introspection.
     *
     * For example, if there's a parameter called 'foo' that has value 'abc',
     * then <tt>bean.setFoo('abc')</tt> will be invoked. This will be repeated
     * for all parameters. Parameters that do not have corresponding setters will
     * be simply ignored.
     *
     * <p>
     * Values are converted into the right type. See {@link ConvertUtils#convert(String, Class)}.
     *
     * @see BeanUtils#setProperty(Object, String, Object)
     *
     * @param bean
     *      The object which will be filled out.
     */
    void bindParameters( Object bean );

    /**
     * Binds form parameters to a bean by using introspection.
     *
     * This method works like {@link #bindParameters(Object)}, but it performs a
     * pre-processing on property names. Namely, only property names that start
     * with the given prefix will be used for binding, and only the portion of the
     * property name after the prefix is used.
     *
     * So for example, if the prefix is "foo.", then property name "foo.bar" with value
     * "zot" will invoke <tt>bean.setBar("zot")</tt>.
     */
    void bindParameters( Object bean, String prefix );

    /**
     * Binds collection form parameters to beans by using introspection or
     * constructor parameters injection.
     *
     * <p>
     * This method works like {@link #bindParameters(Object,String)} and
     * {@link #bindParameters(Class, String)}, but it assumes
     * that form parameters have multiple-values, and use individual values to
     * fill in multiple beans.
     *
     * <p>
     * For example, if <tt>getParameterValues("foo")=={"abc","def"}</tt>
     * and <tt>getParameterValues("bar")=={"5","3"}</tt>, then this method will
     * return two objects (the first with "abc" and "5", the second with
     * "def" and "3".)
     *
     * @param type
     *      Type of the bean to be created. This class must have the default no-arg
     *      constructor.
     *
     * @param prefix
     *      See {@link #bindParameters(Object, String)} for details.
     *
     * @return
     *      Can be empty but never null.
     */
    <T>
    List<T> bindParametersToList( Class<T> type, String prefix );

    /**
     * Instanciates a new object by injecting constructor parameters from the form parameters.
     *
     * <p>
     * The given class must have a constructor annotated with '@stapler-constructor',
     * and must be processed by the maven-stapler-plugin, so that the parameter names
     * of the constructor is available at runtime.
     *
     * <p>
     * The prefix is used to control the form parameter name. For example,
     * if the prefix is "foo." and if the constructor is define as
     * <code>Foo(String a, String b)</code>, then the constructor will be invoked
     * as <code>new Foo(getParameter("foo.a"),getParameter("foo.b"))</code>.
     */
    <T>
    T bindParameters( Class<T> type, String prefix );

    /**
     * Works like {@link #bindParameters(Class, String)} but uses n-th value
     * of all the parameters.
     *
     * <p>
     * This is useful for creating multiple instances from repeated form fields.
     */
    <T>
    T bindParameters( Class<T> type, String prefix, int index );

    /**
     * Data-bind from a {@link JSONObject} to the given target type,
     * by using introspection or constructor parameters injection.
     *
     * <p>
     * For example, if you have a constructor that looks like the following:
     *
     * <pre>
     * class Foo {
     *   &#64;{@link DataBoundConstructor}
     *   public Foo(Integer x, String y, boolean z, Bar bar) { ... }
     * }
     *
     * class Bar {
     *   &#64;{@link DataBoundConstructor}
     *   public Bar(int x, int y) {}
     * }
     * </pre>
     *
     * ... and if JSONObject looks like
     *
     * <pre>{ y:"text", z:true, bar:{x:1,y:2}}</pre>
     *
     * then, this method returns
     *
     * <pre>new Foo(null,"text",true,new Bar(1,2))</pre>
     *
     * <h3>Sub-typing</h3>
     * <p>
     * In the above example, a new instance of <tt>Bar</tt> was created,
     * but you can also create a subtype of Bar by having the 'class' property in
     * JSON like this:
     *
     * <pre>
     * class BarEx extends Bar {
     *   &#64;{@link DataBoundConstructor}
     *   public BarEx(int a, int b, int c) {}
     * }
     *
     * { y:"text", z:true, bar: { class:"p.k.g.BarEx", a:1, b:2, c:3 } }
     * </pre>
     *
     * <p>
     * The type that shows up in the constructor (<tt>Bar</tt> in this case)
     * can be an interface or an abstract class.
     */
    <T>
    T bindJSON(Class<T> type, JSONObject src);

    /**
     * Data-bind from either {@link JSONObject} or {@link JSONArray} to a list,
     * by using {@link #bindJSON(Class, JSONObject)} as the lower-level mechanism.
     *
     * <p>
     * If the source is {@link JSONObject}, the returned list will contain
     * a single item. If it is {@link JSONArray}, each item will be bound.
     * If it is null, then the list will be empty.
     */
    <T>
    List<T> bindJSONToList(Class<T> type, Object src);

    /**
     * Gets the content of the structured form submission.
     * 
     * See http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
     */
    JSONObject getSubmittedForm();
}
