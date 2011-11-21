package com.googlecode.objectify.impl.save;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.appengine.api.datastore.Entity;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Unindex;
import com.googlecode.objectify.impl.TypeUtils;

/**
 * <p>Base class for EmbeddedArrayFieldSaver and EmbeddedCollectionFieldSaver
 * that handles most of the logic.  The subclasses need only understand how to
 * get the component type and how to make an iterator.</p>
 */
abstract public class EmbeddedMultivalueFieldSaver extends FieldSaver
{
	/** Used to actually save the object in the field */
	EmbeddedClassSaver classSaver;

	/**
	 * @param field must be an array type
	 * @param ignoreClassIndexing see the FieldSaver javadocs
	 * @param collectionize must always be false because we cannot nest embedded arrays
	 *  or collections.  This parameter is here so that it is always passed in the code,
	 *  never forgotten, and will always generate the appropriate runtime error.
	 */
	public EmbeddedMultivalueFieldSaver(ObjectifyFactory fact, Class<?> examinedClass, Field field, boolean ignoreClassIndexing)
	{
		super(examinedClass, field, ignoreClassIndexing);
		
		boolean ignoreClassIndexingAnnotations =
			this.field.isAnnotationPresent(Index.class) || this.field.isAnnotationPresent(Unindex.class);
		
		// Now we collectionize everything on down
		// We use our indexed state to define everything below us
		this.classSaver = new EmbeddedClassSaver(fact, this.getComponentType(), ignoreClassIndexingAnnotations, true);
	}
	
	/** Gets the component type of the field */
	abstract protected Class<?> getComponentType();

	/** Gets an iterator from the array or collection passed in */
	abstract protected Collection<Object> asCollection(Object arrayOrCollection);
	
	/* (non-Javadoc)
	 * @see com.googlecode.objectify.impl.save.FieldSaver#saveValue(java.lang.Object, com.google.appengine.api.datastore.Entity, boolean)
	 */
	@Override
	final public void saveValue(Object value, Entity entity, Path path, boolean index)
	{
		Object arrayOrCollection = value;
		if (arrayOrCollection == null)
		{
			// We currently ignore null arrays or collections
			
			// COLLECTION STATE TRACKING:  We could track this state in an out-of-band property.
			// TypeUtils.saveStateProperty(entity, this.path, null);
		}
		else
		{
			Collection<Object> pojos = this.asCollection(arrayOrCollection);
			
			if (pojos.isEmpty())
			{
				// We currently ignore empty arrays or collections
				
				// COLLECTION STATE TRACKING:  We could track this state in an out-of-band property.
				// TypeUtils.saveStateProperty(entity, this.path, 0);
			}
			else
			{
				// We track any nulls and store their indexes.  We put this list of indexes
				// into a collection and store it in an out-of-band property.

				List<Integer> nullIndexes = new ArrayList<Integer>();
				
				int which = 0;
				for (Object embeddedPojo: pojos)
				{
					if (embeddedPojo == null)
					{
						nullIndexes.add(which);
					}
					else
					{
						this.classSaver.save(embeddedPojo, entity, path, index);
					}

					which++;
				}
				
				if (!nullIndexes.isEmpty())
					TypeUtils.setNullIndexes(entity, path, nullIndexes);
			}
		}
	}
}
