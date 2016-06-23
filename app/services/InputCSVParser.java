package services;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

import javax.persistence.EntityManager;

import play.*;

import org.apache.commons.lang.StringUtils;
import utils.Utility;
import exceptions.CSVValidationException;
import exceptions.GLLoadException;
import models.*;
import javax.inject.*;
import play.db.jpa.JPAApi;
import play.db.jpa.Transactional;


public class InputCSVParser implements Runnable {

	
	private final long jobId;
	private final long busArea;
	private final String busAreaName;
	private Dataset dataset;
	private String filename;
	private File inFile;
	/** The _logger. */
    private static final Logger.ALogger logger = Logger.of(InputCSVParser.class);
	/** The Constant END_OF_FILE. */
	static final String END_OF_FILE = "*********";
	private static final int TIME_DIMENSION_ITEM_INDEX = 17;
	private static final int GEOG_AREA_CODE_INDEX = 14;
	private static final int CSV_DIM_ITEM_COLUMN_COUNT = 8;
	private static final int CSV_MIN_COLUMN_COUNT = 43;
	private static final int ATTR_STAT_UNIT_ENGLISH = 2;
	private static final int ATTR_STAT_UNIT_WELSH = 3;
	private static final int ATTR_MEASURE_TYPE_ENGLISH = 4;
	private static final int ATTR_MEASURE_TYPE_WELSH = 5;
	private static final int ATTR_UNIT_MULTIPLIER = 9;
	private static final int ATTR_MEASURE_UNIT_ENGLISH = 10;
	private static final int ATTR_MEASURE_UNIT_WELSH = 11;
	private static final String ALLOWED_ATTR_CHARACTERS = "^[^,\"^]*";
	private static final String ALLOWED_ATTR_ERROR_MSG = "Attributes must not contain characters \" ^ , ";
	EntityManager em;
	DimensionalDataSet dds;
	
	public InputCSVParser(long job, long area, String busAreaName, Dataset ds, String file, File inFile) {
		this.jobId = job;
		this.busArea = area;
		this.filename = file;
		this.inFile = inFile;
		this.dataset = ds;
		this.busAreaName = busAreaName;
	}
	
	public InputCSVParser(Dataset ds, String file) {
		this.jobId = 0;
		this.busArea = 0;
		this.busAreaName = "Global";
		this.filename = file;
		this.dataset = ds;
	}
	
	public InputCSVParser(Dataset ds, File file) {
		this.jobId = 0;
		this.busArea = 0;
		this.busAreaName = "Global";
		this.inFile = file;
		this.filename = "object";
		this.dataset = ds;
	}
	
	public void runJPA(EntityManager em, DimensionalDataSet dds) {
		this.em = em;
		this.dds = dds;
		run();
	}
	
	@Override
	public void run() {
		logger.info(String.format("File loading started for the Job Id: %s.", jobId));
		try {
			String timeStamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
			dds.setModified(timeStamp);
			dds.setValidationException("");
			dds.setLoadException("");
			dds.setValidationMessage("Success");
			parseCSV();
			this.dataset.setStatus("Loaded to staging");
			dds.setStatus("1-Staging-OK");
			logger.info(String.format("Observations successfully loaded for Job %s.", jobId));
		} catch (CSVValidationException validationException) {
			this.dataset.setStatus("Input file failed validation");
			dds.setStatus("1-Staging-Failed");
			dds.setValidationMessage(validationException.getMessage());
			dds.setValidationException(validationException.getLocalizedMessage());
			logger.info(String.format("Observations file failed validation for Job %s : %s", jobId, validationException ));
		} catch (GLLoadException loadException) {
			this.dataset.setStatus("Loading of observations failed");
			dds.setStatus("1-Staging-Failed");
			dds.setValidationException(loadException.getMessage());
			dds.setLoadException(loadException.getMessage());
			logger.info(String.format("Loading of observations into staging was not successful for Job %s : %s", jobId, loadException ));
		} finally {
			em.persist(dds);
		}
	}
	
	@Transactional
	public void parseCSV() {
		// String
		// Column 0 Observation value (number) --observation
		// Column 1 Data marking String --observation
		// Column 2 Statistical Unit Eng value --attribute
		// Column 3 Statistical Unit welsh value --attribute
		// Column 4 Meas type eng --attribute
		// Column 5 Meas type welsh --attribute
		// Column 6-->obs type code --observation
		// Column 7-->empty --ignore
		// Column 8-->obs type val --observation
		// Column 9-->unit mult scalar(english value) --attribute
		// Column 10-->unit of meas eng --attribute
		// Column 11-->unit of meas welsh --attribute
		// Column 12-->confidentiality --observation
		// Column 13-->empty --ignore
		// Column 14-->geog --geog_item
		// Column 15-->empt --ignore
		// Column 16-->empt --ignore
		// Column 17-->Time Dim Item ID --dimension item (CL_TIME)
		// Column 18-->Time Dim Item Label Eng --dimension item
		// Column 19-->Time Dim Item Label Welsh --dimension item
		// Column 20-->time type --classification item type (Year, Month, Quarter)
		// Column 21-->emp --ignore
		// Column 22-->Statistical Population ID -- segment
		// Column 23-->Statistical Population Label Eng -- segment
		// Column 24-->Statistical Population Label welsh -- segment
		// Column 25-->CDID --dim_item_set
		// Column 26-->CDID Description --dim_item_set
		// Column 27-->empt --ignore
		// Column 28-->empt --ignore
		// Column 29-->empt --ignore
		// Column 30-->empt --ignore
		// Column 31-->empt --ignore
		// Column 32-->empt --ignore
		// Column 33-->empt --ignore
		// Column 34-->empt --ignore
		// Column 35-->Dim ID 1
		// Column 36-->Dimension Label Eng 1
		// Column 37-->Dimension Label Welsh 1
		// Column 38-->Dim Item ID 1
		// Column 39-->Dimension Item Label Eng 1
		// Column 40-->Dimension Item Label Cym 1
		// Column 41-->Is Total 1
		// Column 42-->Is Subtotal 1
		// 35-42 cols will be repeated if there are more dimensions
			logger.info("CSV parsing started at:" + new Date());
			String rowData[] = null;
			String firstCellVal = null;
			String secondCellVal = null;
			String thirdCellVal = null;
			int rowLength = 0;
			int rowCounter = 0;
			String csvPath = filename;
			String filePathArray[] = filename.split("/");
			filename = filePathArray[filePathArray.length - 1];
//			BufferedReader csvReader = getCSVBufferedReader(csvPath);
			BufferedReader csvReader = getCSVBufferedReader(inFile);
			CSVParser csvParser = new CSVParser();
			if (csvReader != null) {
				try {
					csvReader.readLine();
					long rowCount = 0L;
					while (csvReader.ready() && (rowData = csvParser.parseLine(csvReader.readLine())) != null) {
						rowLength = rowData.length;
					//	logger.info("rowLength: " + rowLength);
						rowCounter = 0;
						firstCellVal = (rowLength > 0) ? rowData[0] : null;
						secondCellVal = (rowLength > 1) ? rowData[1] : null;
						thirdCellVal = (rowLength > 2) ? rowData[2] : null;
						if (rowData[0].equals(END_OF_FILE)) {
							// Get specified Observation count
							logger.info("parseCSV(): EOF processing - rows processed = " + rowCount);
							if (StringUtils.isNotBlank(rowData[1])) {
								long obsCount = Long.valueOf(rowData[1]);
								if (obsCount != rowCount) {
									throw new CSVValidationException(String.format("Observation count mismatch %d specified %d provided", obsCount, rowCount));
								}
							} else {
								throw new GLLoadException("incorrect information in End of File record");
							}
							break;
						} else if (StringUtils.isNotBlank(firstCellVal) && firstCellVal.contains("*") && firstCellVal.trim().length() < 9) {
							throw new GLLoadException("incorrect information in End of File record");
						} else if (StringUtils.isBlank(firstCellVal) && StringUtils.isNumeric(secondCellVal) && StringUtils.isBlank(thirdCellVal)) {
							throw new GLLoadException("incorrect information in End of File record");
						}
						// Increment the number of rows found
						rowCount++;
						// Validate the row
						validate(rowData, rowCount);
						StageDimensionalDataPoint stageObs = new StageDimensionalDataPoint();
						stageObs.setDimensionalDataSetId(dds.getDimensionalDataSetId());
						if (rowData[0] != null && rowData[0].trim().length() > 0) {
							// observation value
							stageObs.setValue(new BigDecimal(rowData[0]));
						}
						if (rowData[1] != null && rowData[1].trim().length() > 0) {
							// Data marking
							stageObs.setDataMarking(rowData[1]);
						}
						if (rowData[6] != null && rowData[6].trim().length() > 0) {
							// Observation type code
				            stageObs.setObservationType(rowData[6].trim());
						}
						if (rowData[8] != null && rowData[8].trim().length() > 0) {
							// Observation type value
							stageObs.setObservationTypeValue(rowData[8]);
						}
						// Geography data
						String geogCode = (rowData[GEOG_AREA_CODE_INDEX] == null ? rowData[GEOG_AREA_CODE_INDEX] : Utility.removeCommaEqual(rowData[GEOG_AREA_CODE_INDEX]));
						if (geogCode != null && geogCode.trim().length() > 0) {
							// Geography code
							stageObs.setGeographicArea(geogCode);
						}
						else
						{
							// default to UK
							stageObs.setGeographicArea("K02000001");
						}
						// Classification item Time data
						String timeClItemCode = (rowData[TIME_DIMENSION_ITEM_INDEX] == null ? rowData[TIME_DIMENSION_ITEM_INDEX] : Utility.removeCommaEqualApostrophe(rowData[TIME_DIMENSION_ITEM_INDEX]));
						if (timeClItemCode != null && timeClItemCode.length() > 0) {
							stageObs.setTimePeriodCode(timeClItemCode);
							stageObs.setTimePeriodId(0L);
						} else {
							logger.error(String.format("Mandatory file element missing : Time Dimension Item Id. Row : %d.", rowCount));
							throw new CSVValidationException(String.format("Mandatory file element missing : Time Dimension Item Id. Row : %d.", rowCount));
						}
						
						if (rowData[18] != null && rowData[18].trim().length() > 0) {
							// Observation type value
							stageObs.setTimePeriodNameEng(rowData[18]);
						}
						
						if (rowData[20] != null && rowData[20].trim().length() > 0) {
							// Observation type value
							stageObs.setTimeType(rowData[20]);
						}
						em.persist(stageObs);
						int rowSub = 35;
						int dimSub = 0;
						while (rowSub < rowLength) {
	//					logger.info("rowsub = " + rowSub);
						// create new category
						StageCategory stageCat = new StageCategory();
						StageCategoryPK catPK = new StageCategoryPK();
						Long seqNum = stageObs.getObservationSeqId();
						catPK.setObservationSeqId(seqNum);
						//	logger.info("seqid=" + stageObs.getObservationSeqId());
						catPK.setDimensionNumber(dimSub);
						stageCat.setId(catPK);
						dimSub++;
							
						if (rowData[rowSub] != null && rowData[rowSub].trim().length() > 0) {
							// Dimension Id
				            stageCat.setConceptSystemId(rowData[rowSub].trim());
						}
						rowSub = rowSub + 1;
						
						if (rowData[rowSub] != null && rowData[rowSub].trim().length() > 0) {
							// Dimension Label
				            stageCat.setConceptSystemLabelEng(rowData[rowSub].trim());
						}
						rowSub = rowSub + 2;
						if (rowData[rowSub] != null && rowData[rowSub].trim().length() > 0) {
							// Dimension Item Id
				            stageCat.setCategoryId(rowData[rowSub].trim());
						}
						rowSub = rowSub + 1;
						if (rowData[rowSub] != null && rowData[rowSub].trim().length() > 0) {
							// Dimension Item Id
				            stageCat.setCategoryNameEng(rowData[rowSub].trim());
						}
						rowSub = rowSub + 4;
						em.persist(stageCat);
						}
								
					}
					if (!END_OF_FILE.equals(firstCellVal)) {
						throw new GLLoadException("End of File record not found");
					}
				} catch (IOException e) {
					logger.error("Failed to read the input CSV: ", e);
					throw new GLLoadException("Failed to read/write the input/output CSV: ", e);
				} catch (CSVValidationException ve) {
					throw ve;
				} catch (Exception e) {
					logger.error("Failed to load CSV file: ", e);
					throw new GLLoadException("Failed to load CSV file due to " + e.getMessage(), e);
				} finally {
					closeCSVReader(csvReader);
				}
				logger.info("CSV parsing completed at:" + new Date());
			}
	}
	
	/**
	 * Validate the row data passed.
	 * 
	 * @param rowData the row data array
	 * @param rowCount
	 */
	private void validate(String[] rowData, long rowCount) throws CSVValidationException {
		// Check if number of columns in CSV file does not conform to specification (is not 39 or additional of 8
		// thereon i.e 47, 55, 63 etc)
		if (rowData.length < CSV_MIN_COLUMN_COUNT || ((rowData.length - CSV_MIN_COLUMN_COUNT) % CSV_DIM_ITEM_COLUMN_COUNT != 0)) {
			throw new CSVValidationException(String.format("File badly formed. Row : %d.", rowCount));
		}
		// Validate attributes
		validateAttribute(rowData[ATTR_STAT_UNIT_ENGLISH], rowCount);
		validateAttribute(rowData[ATTR_STAT_UNIT_WELSH], rowCount);
		validateAttribute(rowData[ATTR_MEASURE_TYPE_ENGLISH], rowCount);
		validateAttribute(rowData[ATTR_MEASURE_TYPE_WELSH], rowCount);
		validateAttribute(rowData[ATTR_UNIT_MULTIPLIER], rowCount);
		validateAttribute(rowData[ATTR_MEASURE_UNIT_ENGLISH], rowCount);
		validateAttribute(rowData[ATTR_MEASURE_UNIT_WELSH], rowCount);
		//logger.info("row number " + rowCount + " OK");
	}

	/**
	 * Validate the attribute value passed.
	 * 
	 * @param string
	 */
	private void validateAttribute(String value, long rowCount) {
		// Check if attribute contains invalid characters (anything not in [A-Z], [a-z], [0-9], [-, _],[$,�,�])
		if (value != null && !value.trim().isEmpty() && !value.matches(ALLOWED_ATTR_CHARACTERS)) {
			throw new CSVValidationException(String.format("%s. Failed for the attribute '%s' at Row : %d.", ALLOWED_ATTR_ERROR_MSG, value, rowCount));
		}
	}
	
	public List getGeogItemMapAsList(Map map) {
		List list = new ArrayList();
		if (map != null && map.size() > 0) {
			Iterator iter = map.entrySet().iterator();
			while (iter.hasNext()) {
				Entry key = (Entry) iter.next();
				if (key.getValue() != null) {
					list.add(key.getValue());
					map.put(key.getKey(), null);
				}
			}
		}
		((ArrayList) list).trimToSize();
		return list;
	}

	public List getMapAsList(Map map) {
		List list = new ArrayList();
		if (map != null && map.size() > 0) {
			Iterator iter = map.entrySet().iterator();
			while (iter.hasNext()) {
				Entry key = (Entry) iter.next();
				list.add(key.getValue());
			}
		}
		((ArrayList) list).trimToSize();
		return list;
	}

	public CSVReader getCSVReader(String fileName) {
		logger.info("getCSVReader() [" + fileName+"]");
		CSVReader csvReader = null;
		try {
			csvReader = new CSVReader(new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName)), "UTF-8")));
		} catch (IOException e) {
			logger.error("Failed to get the CSVReader: ", e);
			throw new GLLoadException("Failed to get the CSVReader: ", e);
		}
		return csvReader;
	}

	public BufferedReader getCSVBufferedReader(String fileName) {
		logger.info("getCSVBufferedReader() [" + fileName+"]");
		BufferedReader csvReader = null;
		try {
			csvReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName)), "UTF-8"), 32768);
		} catch (IOException e) {
			logger.error("Failed to get the BufferedReader: ", e);
			throw new GLLoadException("Failed to get the BufferedReader: ", e);
		}
		return csvReader;
	}
	
	public BufferedReader getCSVBufferedReader(File inFile) {
//		logger.info("getCSVBufferedReader() [" + fileName+"]");
		BufferedReader csvReader = null;
		try {
			csvReader = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), "UTF-8"), 32768);
		} catch (IOException e) {
			logger.error("Failed to get the BufferedReader: ", e);
			throw new GLLoadException("Failed to get the BufferedReader: ", e);
		}
		return csvReader;
	}

	public CSVWriter getCSVWriter(String fileName) {
		CSVWriter csvWriter = null;
		try {
			csvWriter = new CSVWriter(new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileName), Charset.forName("UTF-8").newEncoder())));
		} catch (IOException e) {
			logger.error("Failed to get the CSVWriter: ", e);
			throw new GLLoadException("Failed to get the CSVWriter: ", e);
		}
		return csvWriter;
	}

	public void closeCSVReader(CSVReader reader) {
		logger.info("closeCSVReader(CSVReader) - > Closing the reader ..." );
		if (reader != null) {
			try {
				reader.close();
			} catch (IOException e) {
				logger.error("Failed while closing the CSVReader: ", e);
			}
		}
	}

	public void closeCSVReader(BufferedReader reader) {
		logger.info("closeCSVReader(BufferedReader) - > Closing the reader ..." );
		if (reader != null) {
			try {
				reader.close();
			} catch (IOException e) {
				logger.error("Failed while closing the CSVReader: ", e);
			}
		}
	}

	public void closeCSVWriter(CSVWriter writer) {
		logger.info("closeCSVWriter() - > Closing the csv writer ..." );
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException e) {
				logger.error("Failed while closing the CSVWriter: ", e);
			}
		}
	}
}
