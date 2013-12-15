package biweekly.property.marshaller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import biweekly.ICalDataType;
import biweekly.Warning;
import biweekly.io.json.JCalValue;
import biweekly.io.xml.XCalElement;
import biweekly.parameter.ICalParameters;
import biweekly.property.FreeBusy;
import biweekly.util.Duration;
import biweekly.util.Period;

/*
 Copyright (c) 2013, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Marshals {@link FreeBusy} properties.
 * @author Michael Angstadt
 */
public class FreeBusyMarshaller extends ICalPropertyMarshaller<FreeBusy> {
	public FreeBusyMarshaller() {
		super(FreeBusy.class, "FREEBUSY", ICalDataType.PERIOD);
	}

	@Override
	protected String _writeText(FreeBusy property) {
		List<Period> values = property.getValues();

		return list(values, new ListCallback<Period>() {
			public String asString(Period period) {
				StringBuilder sb = new StringBuilder();

				if (period.getStartDate() != null) {
					String date = date(period.getStartDate()).write();
					sb.append(date);
				}

				sb.append('/');

				if (period.getEndDate() != null) {
					String date = date(period.getEndDate()).write();
					sb.append(date);
				} else if (period.getDuration() != null) {
					sb.append(period.getDuration());
				}

				return sb.toString();
			}
		});
	}

	@Override
	protected FreeBusy _parseText(String value, ICalDataType dataType, ICalParameters parameters, List<Warning> warnings) {
		return parse(list(value), parameters, warnings);
	}

	@Override
	protected void _writeXml(FreeBusy property, XCalElement element) {
		for (Period period : property.getValues()) {
			XCalElement periodElement = element.append(ICalDataType.PERIOD);

			Date start = period.getStartDate();
			if (start != null) {
				periodElement.append("start", date(start).extended(true).write());
			}

			Date end = period.getEndDate();
			if (end != null) {
				periodElement.append("end", date(end).extended(true).write());
			}

			Duration duration = period.getDuration();
			if (duration != null) {
				periodElement.append("duration", duration.toString());
			}
		}
	}

	@Override
	protected FreeBusy _parseXml(XCalElement element, ICalParameters parameters, List<Warning> warnings) {
		List<XCalElement> periodElements = element.children(ICalDataType.PERIOD);
		if (periodElements.isEmpty()) {
			throw missingXmlElements(ICalDataType.PERIOD);
		}

		FreeBusy prop = new FreeBusy();
		for (XCalElement periodElement : periodElements) {
			String startStr = periodElement.first("start");
			if (startStr == null) {
				warnings.add(Warning.parse(9));
				continue;
			}

			Date start = null;
			try {
				start = date(startStr).tzid(parameters.getTimezoneId(), warnings).parse();
			} catch (IllegalArgumentException e) {
				warnings.add(Warning.parse(10, startStr));
				continue;
			}

			String endStr = periodElement.first("end");
			if (endStr != null) {
				try {
					Date end = date(endStr).tzid(parameters.getTimezoneId(), warnings).parse();
					prop.addValue(start, end);
				} catch (IllegalArgumentException e) {
					warnings.add(Warning.parse(11, endStr));
				}
				continue;
			}

			String durationStr = periodElement.first("duration");
			if (durationStr != null) {
				try {
					Duration duration = Duration.parse(durationStr);
					prop.addValue(start, duration);
				} catch (IllegalArgumentException e) {
					warnings.add(Warning.parse(12, durationStr));
				}
				continue;
			}

			warnings.add(Warning.parse(13));
		}
		return prop;
	}

	@Override
	protected JCalValue _writeJson(FreeBusy property) {
		List<Period> values = property.getValues();
		if (values.isEmpty()) {
			return JCalValue.single("");
		}

		List<String> valuesStr = new ArrayList<String>();
		for (Period period : values) {
			StringBuilder sb = new StringBuilder();
			if (period.getStartDate() != null) {
				String date = date(period.getStartDate()).extended(true).write();
				sb.append(date);
			}

			sb.append('/');

			if (period.getEndDate() != null) {
				String date = date(period.getEndDate()).extended(true).write();
				sb.append(date);
			} else if (period.getDuration() != null) {
				sb.append(period.getDuration());
			}

			valuesStr.add(sb.toString());
		}

		return JCalValue.multi(valuesStr);
	}

	@Override
	protected FreeBusy _parseJson(JCalValue value, ICalDataType dataType, ICalParameters parameters, List<Warning> warnings) {
		return parse(value.asMulti(), parameters, warnings);
	}

	private FreeBusy parse(List<String> periods, ICalParameters parameters, List<Warning> warnings) {
		FreeBusy freebusy = new FreeBusy();

		for (String period : periods) {
			String periodSplit[] = period.split("/");

			if (periodSplit.length < 2) {
				warnings.add(Warning.parse(13));
				continue;
			}

			String startStr = periodSplit[0];
			Date start = null;
			try {
				start = date(startStr).tzid(parameters.getTimezoneId(), warnings).parse();
			} catch (IllegalArgumentException e) {
				warnings.add(Warning.parse(10, startStr));
				continue;
			}

			String endStr = periodSplit[1];
			try {
				Date end = date(endStr).tzid(parameters.getTimezoneId(), warnings).parse();
				freebusy.addValue(start, end);
			} catch (IllegalArgumentException e) {
				//must be a duration
				try {
					Duration duration = Duration.parse(endStr);
					freebusy.addValue(start, duration);
				} catch (IllegalArgumentException e2) {
					warnings.add(Warning.parse(14, endStr));
					continue;
				}
			}
		}

		return freebusy;
	}
}
