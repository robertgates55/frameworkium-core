package com.frameworkium.core.htmlelements.loader.decorator;

import static com.frameworkium.core.htmlelements.loader.HtmlElementLoader.createHtmlElement;
import static com.frameworkium.core.htmlelements.loader.HtmlElementLoader.createTypifiedElement;
import static com.frameworkium.core.htmlelements.loader.decorator.ProxyFactory.createHtmlElementListProxy;
import static com.frameworkium.core.htmlelements.loader.decorator.ProxyFactory.createTypifiedElementListProxy;
import static com.frameworkium.core.htmlelements.loader.decorator.ProxyFactory.createWebElementListProxy;
import static com.frameworkium.core.htmlelements.loader.decorator.ProxyFactory.createWebElementProxy;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.getElementName;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.getGenericParameterClass;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.isHtmlElement;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.isHtmlElementList;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.isTypifiedElement;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.isTypifiedElementList;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.isWebElement;
import static com.frameworkium.core.htmlelements.utils.HtmlElementUtils.isWebElementList;

import com.frameworkium.core.htmlelements.element.HtmlElement;
import com.frameworkium.core.htmlelements.element.TypifiedElement;
import com.frameworkium.core.htmlelements.loader.decorator.proxyhandlers.HtmlElementListNamedProxyHandler;
import com.frameworkium.core.htmlelements.loader.decorator.proxyhandlers.TypifiedElementListNamedProxyHandler;
import com.frameworkium.core.htmlelements.loader.decorator.proxyhandlers.WebElementListNamedProxyHandler;
import com.frameworkium.core.htmlelements.loader.decorator.proxyhandlers.WebElementNamedProxyHandler;
import com.frameworkium.core.htmlelements.pagefactory.CustomElementLocatorFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.util.List;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.pagefactory.ElementLocator;
import org.openqa.selenium.support.pagefactory.ElementLocatorFactory;
import org.openqa.selenium.support.pagefactory.FieldDecorator;

/**
 * Decorator which is used to decorate fields of blocks and page objects.
 * <p/>
 * Will decorate the following fields with lazy proxies:
 * <p/>
 * <ul>
 * <li>{@code WebElement}</li>
 * <li>{@code List<WebElement>}</li>
 * <li>Block of elements ({@link HtmlElement} and its successors)</li>
 * <li>List of blocks</li>
 * <li>Typified element ({@link com.frameworkium.core.htmlelements.element.TypifiedElement} successors)</li>
 * <li>List of typified elements</li>
 * </ul>
 * <p/>
 * {@code WebElements}, lists of {@code WebElements}, typified elements and lists of typified elements
 * have to be marked with {@link org.openqa.selenium.support.FindBy}, {@link org.openqa.selenium.support.FindBys}
 * or {@link org.openqa.selenium.support.FindAll} annotation.
 */
public class HtmlElementDecorator implements FieldDecorator {
  protected ElementLocatorFactory factory;

  public HtmlElementDecorator(CustomElementLocatorFactory factory) {
    this.factory = factory;
  }

  public Object decorate(ClassLoader loader, Field field) {
    try {
      if (isTypifiedElement(field)) {
        return decorateTypifiedElement(loader, field);
      }
      if (isHtmlElement(field)) {
        return decorateHtmlElement(loader, field);
      }
      if (isWebElement(field) && !field.getName().equals("wrappedElement")) {
        return decorateWebElement(loader, field);
      }
      if (isTypifiedElementList(field)) {
        return decorateTypifiedElementList(loader, field);
      }
      if (isHtmlElementList(field)) {
        return decorateHtmlElementList(loader, field);
      }
      if (isWebElementList(field)) {
        return decorateWebElementList(loader, field);
      }
      return null;
    } catch (ClassCastException ignore) {
      return null; // See bug #94 and NonElementFieldsTest
    }
  }

  protected <T extends TypifiedElement> T decorateTypifiedElement(ClassLoader loader, Field field) {
    WebElement elementToWrap = decorateWebElement(loader, field);

    //noinspection unchecked
    return createTypifiedElement((Class<T>) field.getType(), elementToWrap);
  }

  protected <T extends HtmlElement> T decorateHtmlElement(ClassLoader loader, Field field) {
    WebElement elementToWrap = decorateWebElement(loader, field);

    //noinspection unchecked
    return createHtmlElement((Class<T>) field.getType(), elementToWrap);
  }

  protected WebElement decorateWebElement(ClassLoader loader, Field field) {
    ElementLocator locator = factory.createLocator(field);
    InvocationHandler handler = new WebElementNamedProxyHandler(locator, getElementName(field));

    return createWebElementProxy(loader, handler);
  }

  protected <T extends TypifiedElement> List<T> decorateTypifiedElementList(ClassLoader loader,
                                                                            Field field) {
    @SuppressWarnings("unchecked")
    Class<T> elementClass = (Class<T>) getGenericParameterClass(field);
    ElementLocator locator = factory.createLocator(field);
    String name = getElementName(field);

    InvocationHandler handler =
        new TypifiedElementListNamedProxyHandler<>(elementClass, locator, name);

    return createTypifiedElementListProxy(loader, handler);
  }

  protected <T extends HtmlElement> List<T> decorateHtmlElementList(ClassLoader loader,
                                                                    Field field) {
    @SuppressWarnings("unchecked")
    Class<T> elementClass = (Class<T>) getGenericParameterClass(field);
    ElementLocator locator = factory.createLocator(field);
    String name = getElementName(field);

    InvocationHandler handler = new HtmlElementListNamedProxyHandler<>(elementClass, locator, name);

    return createHtmlElementListProxy(loader, handler);
  }

  protected List<WebElement> decorateWebElementList(ClassLoader loader, Field field) {
    ElementLocator locator = factory.createLocator(field);
    InvocationHandler handler = new WebElementListNamedProxyHandler(locator, getElementName(field));

    return createWebElementListProxy(loader, handler);
  }
}
