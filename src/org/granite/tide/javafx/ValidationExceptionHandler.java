package org.granite.tide.javafx;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javafx.event.Event;

import javax.validation.ConstraintViolation;

import org.granite.tide.Context;
import org.granite.tide.rpc.ExceptionHandler;
import org.granite.tide.rpc.TideFaultEvent;
import org.granite.tide.validators.InvalidValue;
import org.granite.util.javafx.DataNotifier;
import org.granite.validation.javafx.ConstraintViolationEvent;

import flex.messaging.messages.ErrorMessage;

public class ValidationExceptionHandler implements ExceptionHandler {

	@Override
	public boolean accepts(ErrorMessage emsg) {
		return emsg.getFaultCode().equals("Validation.Failed");
	}

	@Override
	public void handle(Context context, ErrorMessage emsg, TideFaultEvent faultEvent) {
		Object[] invalidValues = (Object[])emsg.getExtendedData().get("invalidValues");
		if (invalidValues != null) {
			Map<Object, Set<ConstraintViolation<Object>>> violationsMap = new HashMap<Object, Set<ConstraintViolation<Object>>>();
			for (Object v : invalidValues) {
				InvalidValue iv = (InvalidValue)v;
				Object rootBean = context.getEntityManager().getCachedObject(iv.getRootBean(), true);
				Object leafBean = iv.getBean() != null ? context.getEntityManager().getCachedObject(iv.getBean(), true) : null;
				Object bean = leafBean != null ? leafBean : rootBean;
				
				Set<ConstraintViolation<Object>> violations = violationsMap.get(bean);
				if (violations == null) {
					violations = new HashSet<ConstraintViolation<Object>>();
					violationsMap.put(bean, violations);
				}
				
				ServerConstraintViolation violation = new ServerConstraintViolation(iv, rootBean, leafBean);
				violations.add(violation);
			}
			
			for (Object bean : violationsMap.keySet()) {
				if (bean instanceof DataNotifier) {
					ConstraintViolationEvent event = new ConstraintViolationEvent(ConstraintViolationEvent.CONSTRAINT_VIOLATION, violationsMap.get(bean));
					Event.fireEvent((DataNotifier)bean, event);
				}
			}
		}
	}

}
