package io.mehdieidi.modriss.mde.emf;

import java.util.Date;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.xmi.XMLHelper;
import org.eclipse.emf.ecore.xmi.impl.XMIHelperImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;

/** XMI resource with strict, timezone-independent EDate loading and UTC serialization. */
public class DateAwareXmiResource extends XMIResourceImpl {

  public DateAwareXmiResource(URI uri) {
    super(uri);
  }

  @Override
  protected XMLHelper createXMLHelper() {
    return new XMIHelperImpl(this) {
      @Override
      protected Object createFromString(EFactory factory, EDataType type, String value) {
        return type == EcorePackage.Literals.EDATE
            ? EmfDateTime.parse(value)
            : super.createFromString(factory, type, value);
      }

      @Override
      public String convertToString(EFactory factory, EDataType type, Object value) {
        return type == EcorePackage.Literals.EDATE
            ? EmfDateTime.format((Date) value)
            : super.convertToString(factory, type, value);
      }
    };
  }
}
