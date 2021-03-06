package com.ssm.serviceImpl;

import java.util.List;
import javax.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ssm.dao.UserMapper;
import com.ssm.pojo.User;
import com.ssm.service.IUserService;

/**
 * 
 * 缓存机制说明：所有的查询结果都放进了缓存，也就是把MySQL查询的结果放到了redis中去，
 * 然后第二次发起该条查询时就可以从redis中去读取查询的结果，从而不与MySQL交互，从而达到优化的效果，
 * redis的查询速度之于MySQL的查询速度相当于 内存读写速度 /硬盘读写速度
 * 
 * @Cacheable(value="xxx" key="zzz")注解：标注该方法查询的结果进入缓存，再次访问时直接读取缓存中的数据
 * 1.对于有参数的方法，指定value(缓存区间)和key(缓存的key)；
 * 	   对于无参数的方法，只需指定value,存到数据库中数据的key通过com.ssm.utils.RedisCacheConfig中重写的generate()方法生成。
 * 2.调用该注解标识的方法时，会根据value和key去redis缓存中查找数据，如果查找不到，则去数据库中查找，然后将查找到的数据存放入redis缓存中；
 * 3.向redis中填充的数据分为两部分：
 * 		1).用来记录xxx缓存区间中的缓存数据的key的xxx~keys(zset类型)
 * 		2).缓存的数据，key：数据的key；value：序列化后的从数据库中得到的数据
 * 4.第一次执行@Cacheable注解标识的方法，会在redis中新增上面两条数据
 * 5.非第一次执行@Cacheable注解标识的方法，若未从redis中查找到数据，则执行从数据库中查找，并：
 * 		1).新增从数据库中查找到的数据
 * 		2).在对应的zset类型的用来记录缓存区间中键的数据中新增一个值，新增的value为上一步新增的数据的key
 */

/**
 * @Cacheable
 * 可以标记在一个方法上，也可以标记在一个类上。
 * 当标记在一个方法上时表示该方法是支持缓存的，当标记在一个类上时则表示该类所有的方法都是支持缓存的。
 * 对于一个支持缓存的方法，Spring会在其被调用后将其返回值缓存起来，以保证下次利用同样的参数来执行该方法时可以直接从缓存中获取结果，
 * 而不需要再次执行该方法。Spring在缓存方法的返回值时是以键值对进行缓存的，值就是方法的返回结果，至于键的话，Spring又支持两种策略，
 * 默认策略和自定义策略，这个稍后会进行说明。需要注意的是当一个支持缓存的方法在对象内部被调用时是不会触发缓存功能的。
 * @Cacheable可以指定三个属性，value、key和condition。
 * value属性指定Cache名称
 *  使用key属性自定义key
 *   condition属性指定发生的条件
 *   例如@Cacheable(value={"users"}, key="#user.id", condition="#user.id%2==0")
 */

/**
 * @CacheEvict
 * 是用来标注在需要清除缓存元素的方法或类上的。
 * 当标记在一个类上时表示其中所有的方法的执行都会触发缓存的清除操作。
 * @CacheEvict可以指定的属性有value、key、condition、allEntries和beforeInvocation。其中value、key和condition的语义与@Cacheable对应的属性类似。
 * 即value表示清除操作是发生在哪些Cache上的（对应Cache的名称）；key表示需要清除的是哪个key，如未指定则会使用默认策略生成的key；
 * condition表示清除操作发生的条件。下面我们来介绍一下新出现的两个属性allEntries和beforeInvocation。
 * 1.beforeInvocation属性
 * 清除操作默认是在对应方法成功执行之后触发的，即
 * 方法如果因为抛出异常而未能成功返回时也不会触发清除操作。
 * 使用beforeInvocation可以改变触发清除操作的时间，当我们指定该属性值为true时，Spring会在调用该方法之前清除缓存中的指定元素。
 * 2.
 */
@Service("userService")
//事务注解
@Transactional(propagation=Propagation.REQUIRED, rollbackFor=Exception.class)  
public class UserServiceImpl implements IUserService {

    @Resource
    private UserMapper iUserDao;

    /**
     * 根据ID查找user
     * 查到的数据存到users缓存区间，key为user_id，value为序列化后的user对象
     * 
     */
    @Cacheable(value = "aboutUser", key="'user_'+#userId") 
    @Override
    public User getUserById(Integer userId) {
        return iUserDao.selectByPrimaryKey(userId);
    }

    /**
     * 获取所有用户信息
     * 1.对数据一致性要求较高，所以在执行增删改操作后需要将redis中该数据的缓存清空，
     * 从数据库中获取最新数据。
     * 2.若缓存中没有所需的数据，则执行该方法后：
     * 	1).在redis缓存中新增一条数据
     * 		key：getAllUser  value：序列化后的List<User>
     * 		key的值通过com.ssm.utils.RedisCacheConfig中重写的generate()方法生成
     * 	2).在用来记录aboutUser缓存区间中的缓存数据的key的aboutUser~keys(zset类型)中新添加一个value，
     * 	        值为上面新增数据的key
     */
    @Cacheable(value="aboutUser")
    @Override
    public List<User> getAllUser() {
        return iUserDao.selectAllUser();
    }
    
    /**
     * @CacheEvict()注解:移除指定缓存区间的一个或者多个缓存对象
     * @param value + key 或者 value + allEntries=true
     * 1.value + key 移除value缓存区间内的键为key的数据
     * 2.value + allEntries=true 移除value缓存区间内的所有数据
     */
    //@CacheEvict(value= "aboutUser", key="'user_'+#result.id")
    @CacheEvict(value= "aboutUser", allEntries=true)
    @Override
    public User insertUser(User user) {
        iUserDao.insertUser(user);//进行了主键回填
        return user;
    }

    /**
     * 根据id删除用户
     */
    @CacheEvict(value= "aboutUser", allEntries=true)
    @Override
    public int deleteUser(int id) {
        return iUserDao.deleteUser(id);
    }

    /**
     * 根据关键词模糊查询用户，命中率较低，不存入redis缓存中
     */
    @Override
    public List<User> findUsers(String keyWords) {
        return iUserDao.findUsers(keyWords);
    }

    @CacheEvict(value= {"aboutUser"},allEntries=true)
    @Override
    public int editUser(User user) {
        return iUserDao.editUser(user);
    }

    /**
     * 统计当前所有用户ID
     * 1.对数据一致性要求较高，所以在执行增删改操作后需要将redis中该数据的缓存清空，
     * 从数据库中获取最新数据。
     * 2.执行该方法后，在redis缓存中新增两条数据
     * 	1) selectNowIds() 对应方法名,可以在com.ssm.utils.RedisCacheConfig中重写generate()方法自定义
     * 	2) NowIds~key 对应注解参数
     */

	@Cacheable(value = "aboutUser")
	@Override
	public List<Integer> selectNowIds() {
		return iUserDao.selectIds();
	}
	
	/**
	 * 统计注册用户个数
	 * 	对数据一致性要求不高，所以在controller中使用redisTemplate存入redis，
	 * 并指定生存时间为1小时
	 */
	@Override
	public Integer selectUsersCount() {
		return iUserDao.selectUsersCount();
	}
}