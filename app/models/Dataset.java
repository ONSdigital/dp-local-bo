package models;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.data.validation.ValidationError;

public class Dataset {	
	
	
	private String id;
    private String title;
    private String filename; 
    private String status;
    
    
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }     
    
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getFilename() {
        return filename;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }
    public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}

    
    /**
    * Validates Form<DatasetFormData>.
    * Called automatically in the controller by bindFromRequest().
    * 
    * Validation checks include:
    * <ul>
    * <li> Id must be non-empty.
    * <li> Title must be at least nnnn characters?????.
    * <li> Filename must be supplied   
    * </ul>
    *
    * @return Null if valid, or a List[ValidationError] if problems found.
    */
    public List<ValidationError> validate() {

      List<ValidationError> errors = new ArrayList<ValidationError>();
      
      Pattern pattern = Pattern.compile("^[a-zA-Z0-9]*$");            
     
      if (id == null || id.length() == 0) {
        errors.add(new ValidationError("id", "No id was given."));
      }
      else{
           if (!isAlphaNumeric(id)){
                  errors.add(new ValidationError("id", "Id not alpha numeric."));
           }
      }

      if (!isAlphaNumeric(title)){
          errors.add(new ValidationError("title", "title not alpha numeric."));
      }            
      
      else{
          if (title == null || title.length() == 0) {
              errors.add(new ValidationError("title", "No title was given."));
          }
      }            
      
      if(errors.size() > 0){
          return errors;
      }
      else{
        return null;
      }  
    }
    
    public boolean isAlphaNumeric(String s){
      String pattern= "^[a-zA-Z0-9]*$";
      if(s.matches(pattern)){
        return true;
      }
      return false;   
  }
}